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
 * @author Janusch Rentenatus
 */
public class JsonJackCollectionEditorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"woodId", "text", "jsonType"};

    private final List<JsonJackFieldTableModel.InstanceCandidate> candidates;
    private final List<JsonJackFieldTableModel.InstanceCandidate> chosen;

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
     */
    public JsonJackCollectionEditorDialog(Window owner, String fieldLabel,
            List<JsonJackFieldTableModel.InstanceCandidate> candidates,
            List<JsonJackFieldTableModel.InstanceCandidate> current) {
        super(owner, "Collection: " + fieldLabel, ModalityType.APPLICATION_MODAL);
        this.candidates = new ArrayList<>(candidates);
        this.chosen = new ArrayList<>(current);
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
        top.add(duplicatesBox);
        add(top, BorderLayout.NORTH);

        // center: candidates on the left, buttons in the middle, chosen on the right
        candidatesModel = new DefaultTableModel(new Object[][]{}, COLUMNS) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        candidatesTable = new JTable(candidatesModel);
        candidatesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
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
        for (JButton button : new JButton[]{add, remove, up, topBtn, down, bottomBtn}) {
            buttons.add(button);
        }

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        center.add(new JScrollPane(candidatesTable), BorderLayout.WEST);
        center.add(buttons, BorderLayout.CENTER);
        center.add(new JScrollPane(chosenTable), BorderLayout.EAST);
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
