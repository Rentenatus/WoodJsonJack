/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.RerollCompositeCommand;
import de.jare.jsoncasted.editor.command.RerollProvidersCommand;
import de.jare.jsoncasted.editor.command.SetCapturedAttributesCommand;
import de.jare.jsoncasted.editor.core.EditBundle;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.project.BundleReroller;
import de.jare.jsoncasted.project.Partition;
import de.jare.jsoncasted.project.PartitionType;
import de.jare.jsoncasted.project.ProjectDocument;
import de.jare.jsoncasted.project.ProjectFileService;
import de.jare.tree.ui.WoodWindow;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

/**
 * The reroll dialog (concept: CONCEPT_ProjectView.md, section 7.1 - phase 8): the explicit,
 * on-demand identity refresh of one bundle. The dialog lists every tree of the project - every
 * JSON file of every bundle plus every standalone JSON file - preselected (binding: a UI reroll
 * is always project-wide disjoint; a curated subset is possible only by explicit deselection).
 * On confirm the bundle opens as an editor session and the plan executes as ONE composite
 * transaction: one undo step rolls the whole reroll back; the trees stay dirty until the user
 * saves. Dangling references refuse before anything executes.
 *
 * @author Janusch Rentenatus
 */
public class RerollIdsDialog extends JDialog {

    private final WoodWindow window;
    private final File projectRoot;
    private final File bundleFolder;
    private final File mainFile;
    private final List<TreeCandidate> candidates;
    private final List<JCheckBox> boxes = new ArrayList<>();
    private final JTextArea previewArea;
    private final javax.swing.JCheckBox synonymBox;

    /**
     * One tree of the project as the dialog lists it: the file, its label relative to the project
     * root and its parsed identity inventory (the disjoint analysis base).
     */
    private static final class TreeCandidate {

        final File file;
        final String label;
        final BundleReroller.TreeIds ids;

        TreeCandidate(File file, String label, BundleReroller.TreeIds ids) {
            this.file = file;
            this.label = label;
            this.ids = ids;
        }
    }

    private RerollIdsDialog(WoodWindow window, File projectRoot, File bundleFolder, File mainFile,
            List<TreeCandidate> candidates) {
        super(window, "Reroll IDs - " + bundleFolder.getName(), ModalityType.APPLICATION_MODAL);
        this.window = window;
        this.projectRoot = projectRoot;
        this.bundleFolder = bundleFolder;
        this.mainFile = mainFile;
        this.candidates = candidates;

        setLayout(new BorderLayout());
        add(new JLabel("Bundle: " + bundleFolder.getAbsolutePath()), BorderLayout.NORTH);

        JPanel treePanel = new JPanel();
        treePanel.setLayout(new javax.swing.BoxLayout(treePanel, javax.swing.BoxLayout.Y_AXIS));
        for (TreeCandidate candidate : candidates) {
            JCheckBox box = new JCheckBox(candidate.label + "  ("
                    + candidate.ids.getRawIds().size() + " ids, "
                    + candidate.ids.getSynonyms().size() + " synonyms)", true);
            box.setToolTipText("Stay disjoint from this tree: " + candidate.file.getAbsolutePath());
            boxes.add(box);
            treePanel.add(box);
        }
        add(new JScrollPane(treePanel), BorderLayout.CENTER);

        previewArea = new JTextArea(6, 60);
        previewArea.setEditable(false);
        previewArea.setText("All trees are preselected - the reroll is project-wide disjoint.\n"
                + "Deselect only when you deliberately narrow the disjoint scope.\n");
        // the synonym switch: ids always reroll, the wiring names only with the box checked
        synonymBox = new javax.swing.JCheckBox("Reroll provider synonyms too (ids always reroll)", true);
        synonymBox.setToolTipText("Unchecked: the bundle keeps its wiring names, "
                + "every wood id still rerolls and the references follow the new ids");
        JButton rerollButton = new JButton("Reroll");
        rerollButton.addActionListener(e -> executeReroll());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.add(synonymBox);
        buttonRow.add(rerollButton);
        buttonRow.add(cancelButton);

        JPanel south = new JPanel(new BorderLayout());
        south.add(new JScrollPane(previewArea), BorderLayout.CENTER);
        south.add(buttonRow, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(window);
    }

    /**
     * The labels of the trees the dialog would enumerate (the live check proves with this that
     * isolated sources stay outside the identity world - they never join the disjoint set).
     *
     * @param projectRoot the open project root, not null
     * @param document the open project document, not null
     * @param bundleFolder the bundle the reroll would target, not null
     * @return the labels of every tree of the project's json partitions, in scan order
     */
    public static List<String> treeLabelsOf(File projectRoot, ProjectDocument document,
            File bundleFolder) {
        final List<String> labels = new ArrayList<>();
        for (TreeCandidate candidate : enumerateTrees(projectRoot, document, bundleFolder)) {
            labels.add(candidate.label);
        }
        return labels;
    }

    /**
     * The binding default path (concept section 7.1): every tree of the project goes into the
     * disjoint set - the curated subset exists only by explicit deselection in the dialog. The
     * live check drives this rail.
     *
     * @param window the live wood window (session and models)
     * @param projectRoot the open project root, not null
     * @param document the open project document (the trees to enumerate), not null
     * @param bundleFolder the bundle whose identities reroll, not null
     * @param mainFile the bundle's main file, not null
     * @return the outcome - check the status before interpreting the counts
     */
    public static RerollOutcome executeAll(WoodWindow window, File projectRoot,
            ProjectDocument document, File bundleFolder, File mainFile, boolean rerollSynonyms)
            throws de.jare.jsoncasted.io.JsonParseException {
        final List<BundleReroller.TreeIds> disjoint = new ArrayList<>();
        for (TreeCandidate candidate : enumerateTrees(projectRoot, document, bundleFolder)) {
            disjoint.add(candidate.ids);
        }
        return executeReroll(window, bundleFolder, mainFile, disjoint, rerollSynonyms);
    }

    /**
     * Shows the reroll dialog for the given bundle of the open project.
     *
     * @param window the wood window (the dialog's anchor and the session holder)
     * @param projectRoot the open project root, not null
     * @param document the open project document (the partitions to enumerate), not null
     * @param bundleFolder the bundle folder whose identities reroll, not null
     * @param mainFile the bundle's main file (the entry of the editor session), not null
     */
    public static void show(WoodWindow window, File projectRoot, ProjectDocument document,
            File bundleFolder, File mainFile) {
        final List<TreeCandidate> candidates = enumerateTrees(projectRoot, document, bundleFolder);
        new RerollIdsDialog(window, projectRoot, bundleFolder, mainFile, candidates).setVisible(true);
    }

    /**
     * Every tree of the project (concept section 7.1, binding): every JSON file of every bundle
     * plus every standalone JSON file of the json partitions - the descriptor files are project
     * metadata, not wood trees, and stay out. The bundle to reroll is part of the list too (its
     * own current ids are worth avoiding; the plan never reuses a taken value).
     */
    private static List<TreeCandidate> enumerateTrees(File projectRoot, ProjectDocument document,
            File bundleFolder) {
        final List<TreeCandidate> result = new ArrayList<>();
        for (Partition partition : document.getRoots()) {
            if (partition.getType() != PartitionType.JSON) {
                continue;
            }
            final File folder = partition.resolve(projectRoot);
            if (folder == null || !folder.isDirectory()) {
                continue;
            }
            collectTrees(folder, projectRoot, result);
        }
        return result;
    }

    /**
     * Recursive collection of every tree: every JSON file below a json partition counts - the
     * desire is protection, not taxonomy; a curated narrower list is the user's explicit
     * deselection, never this scan. The bundle descriptor files are project metadata, not wood
     * trees, and stay out.
     */
    private static void collectTrees(File folder, File projectRoot, List<TreeCandidate> result) {
        final File[] entries = folder.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectTrees(entry, projectRoot, result);
                continue;
            }
            if (!entry.getName().toLowerCase().endsWith(".json")
                    || ProjectFileService.BUNDLE_FILE.equals(entry.getName())) {
                continue;
            }
            result.add(candidateOf(entry, projectRoot));
        }
    }

    /**
     * Parses one file and scans its identity inventory; a file that does not parse degrades
     * visibly: it joins the list with zero ids and a broken hint label.
     */
    private static TreeCandidate candidateOf(File file, File projectRoot) {
        final String label = relativeLabel(projectRoot, file);
        try {
            final JsonNode root = JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot();
            return new TreeCandidate(file, label, BundleReroller.scan(root, null));
        } catch (Exception broken) {
            return new TreeCandidate(file, label + "  [unreadable: " + broken.getMessage() + "]",
                    BundleReroller.TreeIds.of(new HashSet<>(), new HashSet<>(), new HashSet<>()));
        }
    }

    private static String relativeLabel(File projectRoot, File file) {
        final Path root = projectRoot.toPath().toAbsolutePath().normalize();
        final Path path = file.toPath().toAbsolutePath().normalize();
        return root.relativize(path).toString().replace('\\', '/');
    }

    /**
     * The reroll execution: open the bundle session through the rails, plan against the session
     * identities and the selected disjoint trees, refuse on problems, execute the composite as
     * one history entry of the main tree.
     */
    /** What a reroll run came to - the dialog and the live check read this. */
    public static final class RerollOutcome {

        public enum Status {
            EXECUTED, REFUSED, NOTHING, NO_SESSION, NO_MODEL
        }

        private final Status status;
        private final List<String> problems;
        private final int ids;
        private final int synonyms;
        private final int links;

        RerollOutcome(Status status, List<String> problems, int ids, int synonyms, int links) {
            this.status = status;
            this.problems = problems;
            this.ids = ids;
            this.synonyms = synonyms;
            this.links = links;
        }

        public Status getStatus() {
            return status;
        }

        public List<String> getProblems() {
            return problems;
        }

        public int getIds() {
            return ids;
        }

        public int getSynonyms() {
            return synonyms;
        }

        public int getLinks() {
            return links;
        }

        String summary() {
            return "Rerolled " + ids + " wood ids, rerolled " + synonyms
                    + " provider synonym(s), rewired " + links + " reference(s).\n"
                    + "ONE undo step rolls the whole reroll back (undo in the main file's tab).\n"
                    + "The files stay unsaved - save them when the result looks right.";
        }
    }

    private void executeReroll() {
        try {
            final List<BundleReroller.TreeIds> disjoint = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) {
                    disjoint.add(candidates.get(i).ids);
                }
            }
            final RerollOutcome outcome = executeReroll(window, bundleFolder, mainFile, disjoint, synonymBox.isSelected());
            switch (outcome.status) {
                case REFUSED -> previewArea.setText(
                        String.join("\n", outcome.problems)
                        + "\n\nNothing was changed - fix the dangling references first.");
                case NOTHING -> previewArea.setText(
                        "Nothing to reroll: the bundle declares no wood identities, "
                        + "no provider synonyms and no references.");
                case NO_SESSION -> window.showErrorDialog(
                        "Could not open " + mainFile.getName() + " as a bundle session.");
                case NO_MODEL -> window.showErrorDialog(
                        "No editor tab shows the bundle main tree - cannot execute.");
                default -> {
                }
            }
            if (outcome.status == RerollOutcome.Status.EXECUTED) {
                previewArea.setText(outcome.summary());
                JOptionPane.showMessageDialog(this, outcome.summary(), "Reroll executed",
                        JOptionPane.INFORMATION_MESSAGE);
                dispose();
            }
        } catch (de.jare.jsoncasted.io.JsonParseException e) {
            window.showErrorDialog("The bundle session could not be read: " + e.getMessage());
        } catch (RuntimeException e) {
            window.showErrorDialog("The reroll failed: " + e.getMessage());
        }
    }

    /**
     * The reroll execution core (no dialog dependency - the live check drives the same rail):
     * open the bundle as an editor session through the real rails, plan against the session
     * identities and the given disjoint trees, refuse on problems (nothing changes then),
     * execute the composite as ONE history entry of the main tree.
     *
     * @param window the live wood window (session and models)
     * @param bundleFolder the bundle whose identities reroll
     * @param mainFile the bundle's main file (the session entry)
     * @param disjoint the trees to stay disjoint from (the UI passes all project trees)
     * @return the outcome - check the status before interpreting the counts
     */
    static RerollOutcome executeReroll(WoodWindow window, File bundleFolder, File mainFile,
            List<BundleReroller.TreeIds> disjoint, boolean rerollSynonyms) throws de.jare.jsoncasted.io.JsonParseException {
        // open the bundle through the real rails - the detour of the editor session is the point
        window.getMainActions().loadJsonBundle(mainFile);
        final EditBundle session = window.getEditBundle();
        if (session == null) {
            return new RerollOutcome(RerollOutcome.Status.NO_SESSION, new ArrayList<>(), 0, 0, 0);
        }
        final List<EditTree> trees = sessionTrees(session);

        // the bundle's identity inventory off the session trees
        final Set<String> bundleRawIds = new HashSet<>();
        final Set<String> bundleLinks = new HashSet<>();
        for (EditTree tree : trees) {
            walkObjects(tree.getRoot(), obj -> {
                if (obj.getObjektId() != null && !obj.getObjektId().isEmpty()) {
                    bundleRawIds.add(obj.getObjektId());
                }
                if (obj.isLinkReference() && obj.getLinkTarget() != null) {
                    bundleLinks.add(obj.getLinkTarget());
                }
            });
        }
        // the provider synonyms: inside the bundle (rerolled) vs outside (kept)
        final Set<String> bundleSynonyms = new HashSet<>();
        final Set<String> externalSynonyms = new HashSet<>();
        // classification first, then the real plan (the classification feeds the synonym sets)
        final BundleReroller.RerollPlan plan = classifyProvidersAndPlan(session, trees, bundleFolder,
                bundleRawIds, bundleLinks, bundleSynonyms, externalSynonyms, disjoint, rerollSynonyms);
        if (plan.isRefused()) {
            return new RerollOutcome(RerollOutcome.Status.REFUSED, plan.getProblems(), 0, 0, 0);
        }
        if (plan.getIdCount() == 0 && plan.getSynonymCount() == 0 && plan.getLinkCount() == 0) {
            return new RerollOutcome(RerollOutcome.Status.NOTHING, new ArrayList<>(), 0, 0, 0);
        }

        // the children: identity and rewrite commands per node, the synonym reroll per entry
        final List<EditCommand> commands = new ArrayList<>();
        final List<RerollProvidersCommand.EntryRelabel> relabels = new ArrayList<>();
        final List<RerollProvidersCommand.ProviderFlip> flips = new ArrayList<>();
        for (EditTree tree : trees) {
            walkObjects(tree.getRoot(), obj -> {
                if (obj.getObjektId() != null && plan.getIdMap().containsKey(obj.getObjektId())) {
                    commands.add(new SetCapturedAttributesCommand(obj, Map.of(
                            "objektId", plan.getIdMap().get(obj.getObjektId()))));
                }
                if (obj.isLinkReference() && plan.getLinkMap().containsKey(obj.getLinkTarget())) {
                    commands.add(new SetCapturedAttributesCommand(obj, Map.of(
                            "|linkTarget", plan.getLinkMap().get(obj.getLinkTarget()))));
                }
            });
        }
        collectProviderRelabels(session, trees, bundleFolder, plan, relabels, flips);
        if (!relabels.isEmpty()) {
            commands.add(new RerollProvidersCommand(relabels, flips));
        }
        final RerollCompositeCommand transaction = new RerollCompositeCommand(
                "Reroll IDs of " + bundleFolder.getName(), commands, trees);

        // one entry on the main tree's history - one undo step rolls the whole reroll back
        final de.jare.tree.control.model.JackTreeModel model = window.findModelOf(session.getMainTree());
        if (model == null) {
            return new RerollOutcome(RerollOutcome.Status.NO_MODEL, new ArrayList<>(), 0, 0, 0);
        }
        window.getUndoManager().getManagerFor(model).getHistory().execute(transaction);

        // repaint every affected tab - the composite leaves the repaint to its caller
        for (EditTree tree : trees) {
            final de.jare.tree.control.model.JackTreeModel treeModel = window.findModelOf(tree);
            if (treeModel != null) {
                treeModel.rebuildFromDomain();
            }
        }
        window.refreshOutdatedMarkers();
        return new RerollOutcome(RerollOutcome.Status.EXECUTED, new ArrayList<>(),
                plan.getIdCount(), plan.getSynonymCount(), plan.getLinkCount());
    }

    /**
     * The session trees of the bundle: the main tree plus every synonym-registered tree.
     */
    private static List<EditTree> sessionTrees(EditBundle session)
            throws de.jare.jsoncasted.io.JsonParseException {
        final List<EditTree> trees = new ArrayList<>();
        trees.add(session.getMainTree());
        for (String synonym : session.getSortedSynonyms()) {
            final EditTree tree = session.findBySynonym(synonym);
            if (tree != null && !trees.contains(tree)) {
                trees.add(tree);
            }
        }
        return trees;
    }

    /**
     * The provider classification (inside vs outside the bundle) and the real plan. The entries of
     * every session tree's captured providers member resolve relative to their file: an entry
     * whose file lies inside the bundle folder carries a bundle synonym (it rerolls), everything
     * else is external (it stays).
     */
    private static BundleReroller.RerollPlan classifyProvidersAndPlan(EditBundle session,
            List<EditTree> trees, File bundleFolder, Set<String> bundleRawIds, Set<String> bundleLinks,
            Set<String> bundleSynonyms, Set<String> externalSynonyms,
            List<BundleReroller.TreeIds> disjoint, boolean rerollSynonyms) throws de.jare.jsoncasted.io.JsonParseException {
        for (EditTree tree : trees) {
            final JsonNode providers = tree.getProvidersNode();
            final List<JsonNode> entries = providers != null ? providers.asArray() : null;
            if (entries == null) {
                continue;
            }
            final File baseFolder = baseFolderOf(tree);
            for (JsonNode entry : entries) {
                final Map<String, JsonNode> members = entry != null
                        ? entry.asObjectValues() : null;
                final JsonNode synonymNode = members != null ? members.get("synonym") : null;
                final JsonNode filenameNode = members != null ? members.get("filename") : null;
                if (synonymNode == null || synonymNode.asText() == null
                        || synonymNode.asText().isEmpty()) {
                    continue;
                }
                final String synonym = synonymNode.asText();
                final File referenced = filenameNode != null && filenameNode.asText() != null
                        ? new File(baseFolder, filenameNode.asText()) : null;
                if (referenced != null && isInside(bundleFolder, referenced)) {
                    bundleSynonyms.add(synonym);
                } else if (referenced != null) {
                    externalSynonyms.add(synonym);
                }
                // a dangling filename stays unclassified: the plan treats its links as dangling
                // when nothing else declares the synonym
            }
        }
        // the own prefix of the session trees: the sub synonyms identify their trees, the main
        // keeps this/self - both belong to the bundle's synonym world for link qualification
        for (EditTree tree : trees) {
            final String provider = providerOf(session, tree);
            if (provider != null && !"this".equals(provider) && !"self".equals(provider)) {
                bundleSynonyms.add(provider);
            }
        }
        return BundleReroller.plan(bundleRawIds, bundleSynonyms, externalSynonyms, bundleLinks, disjoint, rerollSynonyms);
    }

    /**
     * The provider relabel data: every captured providers entry whose synonym the plan rerolls.
     */
    private static void collectProviderRelabels(EditBundle session, List<EditTree> trees, File bundleFolder,
            BundleReroller.RerollPlan plan,
            List<RerollProvidersCommand.EntryRelabel> relabels,
            List<RerollProvidersCommand.ProviderFlip> flips)
            throws de.jare.jsoncasted.io.JsonParseException {
        for (EditTree tree : trees) {
            final JsonNode providers = tree.getProvidersNode();
            final List<JsonNode> entries = providers != null ? providers.asArray() : null;
            if (entries == null) {
                continue;
            }
            final File baseFolder = baseFolderOf(tree);
            for (JsonNode entry : entries) {
                final Map<String, JsonNode> members = entry != null
                        ? entry.asObjectValues() : null;
                final JsonNode synonymNode = members != null ? members.get("synonym") : null;
                if (synonymNode == null || synonymNode.asText() == null) {
                    continue;
                }
                final String oldSynonym = synonymNode.asText();
                final String newSynonym = plan.getSynonymMap().get(oldSynonym);
                if (newSynonym == null) {
                    continue; // external or untouched
                }
                relabels.add(new RerollProvidersCommand.EntryRelabel(entry, oldSynonym, newSynonym));
                final EditTree identified = session.findBySynonym(oldSynonym);
                if (identified != null && !flippedTree(flips, identified)) {
                    flips.add(new RerollProvidersCommand.ProviderFlip(session, identified,
                            oldSynonym, newSynonym));
                }
            }
        }
    }

    private static boolean flippedTree(List<RerollProvidersCommand.ProviderFlip> flips, EditTree tree) {
        for (RerollProvidersCommand.ProviderFlip flip : flips) {
            if (flip.tree == tree) {
                return true;
            }
        }
        return false;
    }

    /**
     * The folder a tree's providers filenames resolve against: its own file's folder.
     */
    private static File baseFolderOf(EditTree tree) {
        if (tree.getBaseSnapshot() != null && tree.getBaseSnapshot().hasResourceFile()) {
            return new File(tree.getBaseSnapshot().getResourceFile()).getParentFile();
        }
        return null;
    }

    /**
     * The session synonym of a tree (the word the bundle addresses it by), or null.
     */
    private static String providerOf(EditBundle session, EditTree tree)
            throws de.jare.jsoncasted.io.JsonParseException {
        for (String synonym : session.getSortedSynonyms()) {
            if (session.findBySynonym(synonym) == tree) {
                return synonym;
            }
        }
        return null;
    }

    private static boolean isInside(File folder, File candidate) {
        final Path folderPath = folder.toPath().toAbsolutePath().normalize();
        return candidate.toPath().toAbsolutePath().normalize().startsWith(folderPath);
    }

    /**
     * Walks every object node of the tree (the wood identities and reference stubs live on the
     * objects, including array elements).
     */
    private static void walkObjects(EditNodeAbstract node, Consumer<EditNodeObject> visitor) {
        if (node instanceof EditNodeObject obj) {
            visitor.accept(obj);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                walkObjects(child, visitor);
            }
        }
    }

}
