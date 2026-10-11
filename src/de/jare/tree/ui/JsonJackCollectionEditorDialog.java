package de.jare.tree.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

/**
 * Collection editor dialog for object list fields, in the style of the Eclipse
 * Modeling Framework: a filter field and a duplicates checkbox on top, the
 * available instances on the left (columns woodId, text and jsonType - the type
 * column is what makes interface types usable), the chosen elements on the
 * right, move buttons in the middle and Cancel/Accept at the bottom.
 *
 * <p>With the duplicates checkbox unchecked the left side hides everything that
 * is already on the right; with duplicates allowed, entries stay selectable.</p>
 *
 * <p>The dialog follows a target cardinality: {@link Cardinality#MANY} keeps the
 * full list behavior, {@link Cardinality#SINGLE} edits a single object reference
 * - "&gt;" replaces the value instead of appending, the sorting buttons and the
 * duplicates checkbox are hidden, and the candidate table allows no multi
 * selection. Empty or one entry is always accepted.</p>
 *
 * @author Janusch Rentenatus
 */
public class JsonJackCollectionEditorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Target cardinality of the dialog. MANY edits a list, SINGLE a single object reference
     * where the choice replaces the value and empty means null.
     */
    public enum Cardinality {
        MANY, SINGLE
    }

    private static final String[] COLUMNS = {"woodId", "text", "jsonType"};

    private final List<JsonJackFieldTableModel.InstanceCandidate> candidates;
    private final List<JsonJackFieldTableModel.InstanceCandidate> chosen;
    private final Cardinality cardinality;

    private JTextField filterField;
    private JCheckBox duplicatesBox;
    private JTable candidatesTable;
    private DefaultTableModel candidatesModel;
    private DefaultTableModel chosenModel;
    private boolean accepted;

    /**
     * Creates the modal collection editor.
     *
     * @param owner the parent window
     * @param fieldLabel the label of the edited field
     * @param candidates the available instances
     * @param current the elements currently recorded in the list
     * @param cardinality the target cardinality of the edited field
     */
    public JsonJackCollectionEditorDialog(Window owner, String fieldLabel,
            List<JsonJackFieldTableModel.InstanceCandidate> candidates,
            List<JsonJackFieldTableModel.InstanceCandidate> current,
            Cardinality cardinality) {
        super(owner, (cardinality == Cardinality.SINGLE ? "Reference: " : "Collection: ") + fieldLabel,
                ModalityType.APPLICATION_MODAL);
        this.candidates = new ArrayList<>(candidates);
        this.chosen = new ArrayList<>(current);
        this.cardinality = cardinality;
        if (cardinality == Cardinality.SINGLE && this.chosen.size() > 1) {
            // a malformed state keeps its first entry instead of smuggling a list through the single dialog
            this.chosen.subList(1, this.chosen.size()).clear();
        }
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi();
        setSize(760, 460);
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        // top: filter and duplicates checkbox
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        top.add(new JLabel("Filter:"));
        filterField = new JTextField(18);
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refreshCandidates();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refreshCandidates();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refreshCandidates();
            }
        });
        top.add(filterField);
        duplicatesBox = new JCheckBox("duplicates allowed", true);
        duplicatesBox.addActionListener(e -> refreshCandidates());
        if (cardinality != Cardinality.SINGLE) {
            // single mode: no duplicates possible, the choice replaces the value
            top.add(duplicatesBox);
        }
        add(top, BorderLayout.NORTH);

        // center: candidates on the left, buttons in the middle, chosen on the right
        candidatesModel = new DefaultTableModel(new Object[][]{}, COLUMNS) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        candidatesTable = new JTable(candidatesModel);
        candidatesTable.setSelectionMode(cardinality == Cardinality.SINGLE
                ? ListSelectionModel.SINGLE_SELECTION
                : ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        chosenModel = new DefaultTableModel(new Object[][]{}, COLUMNS) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable chosenTable = new JTable(chosenModel);
        chosenTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        final JButton add = new JButton(">");
        add.setToolTipText("add the selected instances to the list");
        add.addActionListener(e -> moveSelectedToChosen());
        final JButton remove = new JButton("<");
        remove.setToolTipText("remove the selected entries from the list");
        remove.addActionListener(e -> removeSelectedFromChosen(chosenTable));
        final JButton up = new JButton("\u2191");
        up.setToolTipText("move the selected entry up");
        up.addActionListener(e -> moveChosen(chosenTable, -1));
        final JButton topBtn = new JButton("\u21D1");
        topBtn.setToolTipText("move the selected entry to the top");
        topBtn.addActionListener(e -> moveChosen(chosenTable, Integer.MIN_VALUE));
        final JButton down = new JButton("\u2193");
        down.setToolTipText("move the selected entry down");
        down.addActionListener(e -> moveChosen(chosenTable, 1));
        final JButton bottomBtn = new JButton("\u21D3");
        bottomBtn.setToolTipText("move the selected entry to the bottom");
        bottomBtn.addActionListener(e -> moveChosen(chosenTable, Integer.MAX_VALUE));
        if (cardinality == Cardinality.SINGLE) {
            buttons.add(add);
            buttons.add(remove);
        } else {
            for (JButton button : new JButton[]{add, remove, up, topBtn, down, bottomBtn}) {
                buttons.add(button);
            }
        }

        // GridBagLayout on purpose: only the two tables get growth weight, the button column keeps
        // its preferred width however wide the dialog becomes - the tables take the extra space.
        JPanel center = new JPanel(new java.awt.GridBagLayout());
        center.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        final java.awt.GridBagConstraints tableConstraints = new java.awt.GridBagConstraints();
        tableConstraints.gridy = 0;
        tableConstraints.fill = java.awt.GridBagConstraints.BOTH;
        tableConstraints.weightx = 1.0;
        tableConstraints.weighty = 1.0;
        tableConstraints.gridx = 0;
        center.add(new JScrollPane(candidatesTable), tableConstraints);
        final java.awt.GridBagConstraints buttonConstraints = new java.awt.GridBagConstraints();
        buttonConstraints.gridy = 0;
        buttonConstraints.gridx = 1;
        buttonConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        buttonConstraints.weightx = 0.0;
        buttonConstraints.weighty = 1.0;
        center.add(buttons, buttonConstraints);
        final java.awt.GridBagConstraints chosenConstraints = new java.awt.GridBagConstraints();
        chosenConstraints.gridy = 0;
        chosenConstraints.gridx = 2;
        chosenConstraints.fill = java.awt.GridBagConstraints.BOTH;
        chosenConstraints.weightx = 1.0;
        chosenConstraints.weighty = 1.0;
        center.add(new JScrollPane(chosenTable), chosenConstraints);
        candidatesTable.setPreferredScrollableViewportSize(new Dimension(280, 300));
        chosenTable.setPreferredScrollableViewportSize(new Dimension(280, 300));
        add(center, BorderLayout.CENTER);

        // bottom: cancel and accept
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            accepted = false;
            dispose();
        });
        final JButton accept = new JButton("Accept");
        accept.addActionListener(e -> {
            accepted = true;
            dispose();
        });
        bottom.add(cancel);
        bottom.add(accept);
        add(bottom, BorderLayout.SOUTH);

        refreshChosen();
        refreshCandidates();
    }

    /**
     * Returns whether the dialog was closed with Accept.
     *
     * @return true when the result is valid
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Returns the chosen elements in list order.
     *
     * @return the result, never null
     */
    public List<JsonJackFieldTableModel.InstanceCandidate> getResult() {
        return new ArrayList<>(chosen);
    }

    private void moveSelectedToChosen() {
        if (cardinality == Cardinality.SINGLE) {
            // single reference: the choice replaces the value, never appends
            chosen.clear();
            final int viewRow = candidatesTable.getSelectedRow();
            if (viewRow >= 0) {
                chosen.add(candidates.get(candidatesTable.convertRowIndexToModel(viewRow)));
            }
            refreshChosen();
            refreshCandidates();
            return;
        }
        final int[] selected = candidatesTable.getSelectedRows();
        for (int viewRow : selected) {
            final JsonJackFieldTableModel.InstanceCandidate candidate
                    = candidates.get(candidatesTable.convertRowIndexToModel(viewRow));
            if (!duplicatesBox.isSelected() && chosen.contains(candidate)) {
                continue; // without duplicates an entry exists at most once
            }
            chosen.add(candidate);
        }
        refreshChosen();
        refreshCandidates();
    }

    private void removeSelectedFromChosen(JTable chosenTable) {
        final int[] selected = chosenTable.getSelectedRows();
        for (int i = selected.length - 1; i >= 0; i--) {
            chosen.remove(chosenTable.convertRowIndexToModel(selected[i]));
        }
        refreshChosen();
        refreshCandidates();
    }

    private void moveChosen(JTable chosenTable, int direction) {
        final int viewRow = chosenTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int index = chosenTable.convertRowIndexToModel(viewRow);
        final JsonJackFieldTableModel.InstanceCandidate entry = chosen.remove(index);
        if (direction == Integer.MIN_VALUE) {
            chosen.add(0, entry);
            index = 0;
        } else if (direction == Integer.MAX_VALUE) {
            chosen.add(entry);
            index = chosen.size() - 1;
        } else {
            index = Math.max(0, Math.min(chosen.size(), index + direction));
            chosen.add(index, entry);
        }
        refreshChosen();
        chosenTable.getSelectionModel().setSelectionInterval(index, index);
    }

    private void refreshCandidates() {
        final String needle = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
        final DefaultTableModel model = candidatesModel;
        model.setRowCount(0);
        for (JsonJackFieldTableModel.InstanceCandidate candidate : candidates) {
            if (!duplicatesBox.isSelected() && chosen.contains(candidate)) {
                continue;
            }
            if (!needle.isEmpty() && !matches(candidate, needle)) {
                continue;
            }
            model.addRow(new Object[]{candidate.woodId(), candidate.text(), candidate.jsonType()});
        }
    }

    private static boolean matches(JsonJackFieldTableModel.InstanceCandidate candidate, String needle) {
        return containsIgnoreCase(candidate.woodId(), needle)
                || containsIgnoreCase(candidate.text(), needle)
                || containsIgnoreCase(candidate.jsonType(), needle);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void refreshChosen() {
        chosenModel.setRowCount(0);
        for (JsonJackFieldTableModel.InstanceCandidate candidate : chosen) {
            chosenModel.addRow(new Object[]{candidate.woodId(), candidate.text(), candidate.jsonType()});
        }
    }
}
