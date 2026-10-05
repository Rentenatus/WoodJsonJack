/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.parse;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Soft parse test over the committed test assets: ParseConfigTemplate.json
 * references the Seed model description ConfigDesc.json and carries the
 * intentional field error "matrix". The on-the-fly parser must report matrix
 * as the only problem, and the tree must be completely green after matrix is
 * removed.
 *
 * @author Janusch Rentenatus
 */
public class SoftParseNGTest {

    /**
     * The template parses, the model description resolves, and matrix is the
     * only node whose edit status is ERROR. Everything outside the matrix
     * subtree is OKAY - including the map entries (settings, labels,
     * enablements), which have no model fields of their own.
     */
    @Test
    public void testMatrixIsTheOnlyParseProblem() throws Exception {
        final EditTree tree = loadTemplate();

        final List<EditNodeAbstract> notOkay = collectNotOkay(tree.getRoot(), new ArrayList<>());
        final List<EditNodeAbstract> errors = notOkay.stream()
                .filter(n -> n.getEditStatus() == EditStatus.ERROR)
                .toList();

        assertEquals(errors.size(), 1, "Exactly one ERROR node is expected: " + names(errors));
        assertEquals(errors.get(0).getName(), "matrix", "The single ERROR must be the matrix property");        assertTrue(errors.get(0).getEditMessage() != null
                && errors.get(0).getEditMessage().contains("unknown in model"),
                "The matrix property must be reported as unknown in the model: "
                + errors.get(0).getEditMessage());

        // Everything outside the matrix subtree must be OKAY.
        final EditNode matrix = errors.get(0);
        for (EditNodeAbstract node : notOkay) {
            assertTrue(isInSubtree(matrix, node),
                    "No node outside the matrix subtree may carry a problem: "
                    + node.getName() + " -> " + node.getEditStatus() + ": " + node.getEditMessage());
        }
    }

    /**
     * After the matrix property is removed and the tree settles again, every
     * node parses OKAY - the tree is green.
     */
    @Test
    public void testTreeIsGreenAfterMatrixRemoval() throws Exception {
        final EditTree tree = loadTemplate();
        final EditNodeObject root = (EditNodeObject) tree.getRoot();
        final EditNode matrix = findChild(root, "matrix");
        assertNotNull(matrix, "The template must contain the matrix property");

        assertTrue(tree.removeNode((EditNodeAbstract) matrix), "The matrix node must be removable");
        waitForParser(tree);

        final List<EditNodeAbstract> notOkay = collectNotOkay(tree.getRoot(), new ArrayList<>());
        assertTrue(notOkay.isEmpty(), "Every node must be OKAY after matrix removal: " + names(notOkay));
    }

    // ========== Helpers ==========
    private EditTree loadTemplate() throws Exception {
        final File template = findAsset("ParseConfigTemplate.json");
        final EditTree tree = JsonTreeConverter.fromJsonFile(template);
        assertNotNull(tree, "The template must load into an edit tree");
        waitForParser(tree);
        return tree;
    }

    private File findAsset(String fileName) {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 4 && dir != null; i++) {
            File candidate = new File(dir, "test_assets/assets/parse/" + fileName);
            if (candidate.exists()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        throw new AssertionError("Test asset not found: test_assets/assets/parse/" + fileName
                + " (working dir: " + System.getProperty("user.dir") + ")");
    }

    private static EditNode findChild(EditNodeObject parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i).getName().equals(name)) {
                return parent.getChildAt(i);
            }
        }
        return null;
    }

    private static List<EditNodeAbstract> collectNotOkay(EditNode node, List<EditNodeAbstract> out) {
        if (node instanceof EditNodeAbstract abs && abs.getEditStatus() != EditStatus.OKAY) {
            out.add(abs);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNotOkay(node.getChildAt(i), out);
        }
        return out;
    }

    private static boolean isInSubtree(EditNode root, EditNode candidate) {
        if (root == candidate) {
            return true;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (isInSubtree(root.getChildAt(i), candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String names(List<EditNodeAbstract> nodes) {
        final StringBuilder sb = new StringBuilder();
        for (EditNodeAbstract n : nodes) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(n.getName()).append(" -> ").append(n.getEditStatus());
        }
        return sb.toString();
    }

    private static void waitForParser(EditTree tree) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                Thread.sleep(150);
                if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Parser did not settle within the timeout");
    }
}
