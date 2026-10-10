package de.jare.tree.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

/**
 * List editor dialog for primitive array values (string annotations, lists of
 * primitives): an editable single column table with the buttons "+", "-", the
 * single and double move arrows for the order, and Cancel/Accept at the bottom.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackPrimitiveListEditorDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"value"};

    private final DefaultTableModel model;
    private boolean accepted;

    /**
     * Creates the modal primitive list editor.
     *
     * @param owner the parent window
     * @param fieldLabel the label of the edited field
     * @param current the values currently recorded in the list
     */
    public JsonJackPrimitiveListEditorDialog(Window owner, String fieldLabel, List<String> current) {
        super(owner, "List: " + fieldLabel, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        model = new DefaultTableModel(new Object[][]{}, COLUMNS);
        for (String value : current) {
            model.addRow(new Object[]{value == null ? "" : value});
        }
        buildUi();
        setSize(420, 360);
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        final JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        final JButton add = new JButton("+");
        add.setToolTipText("add an entry below the selection");
        add.addActionListener(e -> addRow(table));
        final JButton remove = new JButton("-");
        remove.setToolTipText("remove the selected entry");
        remove.addActionListener(e -> removeRow(table));
        final JButton up = new JButton("\u2191");
        up.setToolTipText("move the selected entry up");
        up.addActionListener(e -> moveRow(table, -1));
        final JButton topBtn = new JButton("\u21D1");
        topBtn.setToolTipText("move the selected entry to the top");
        topBtn.addActionListener(e -> moveRow(table, Integer.MIN_VALUE));
        final JButton down = new JButton("\u2193");
        down.setToolTipText("move the selected entry down");
        down.addActionListener(e -> moveRow(table, 1));
        final JButton bottomBtn = new JButton("\u21D3");
        bottomBtn.setToolTipText("move the selected entry to the bottom");
        bottomBtn.addActionListener(e -> moveRow(table, Integer.MAX_VALUE));
        for (JButton button : new JButton[]{add, remove, up, topBtn, down, bottomBtn}) {
            buttons.add(button);
        }
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(buttons, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

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
     * Returns the edited values in list order.
     *
     * @return the result, never null
     */
    public List<String> getResult() {
        final List<String> result = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            final Object value = model.getValueAt(row, 0);
            result.add(value == null ? "" : value.toString());
        }
        return result;
    }

    private void addRow(JTable table) {
        int index = table.getSelectedRow() + 1;
        if (index <= 0) {
            index = model.getRowCount();
        }
        model.insertRow(index, new Object[]{""});
        table.getSelectionModel().setSelectionInterval(index, index);
    }

    private void removeRow(JTable table) {
        final int index = table.getSelectedRow();
        if (index < 0 || index >= model.getRowCount()) {
            return;
        }
        model.removeRow(index);
        if (model.getRowCount() > 0) {
            final int select = Math.min(index, model.getRowCount() - 1);
            table.getSelectionModel().setSelectionInterval(select, select);
        }
    }

    private void moveRow(JTable table, int direction) {
        final int index = table.getSelectedRow();
        if (index < 0) {
            return;
        }
        final Object[] entry = new Object[]{model.getValueAt(index, 0)};
        model.removeRow(index);
        int target;
        if (direction == Integer.MIN_VALUE) {
            target = 0;
        } else if (direction == Integer.MAX_VALUE) {
            target = model.getRowCount();
        } else {
            target = Math.max(0, Math.min(model.getRowCount(), index + direction));
        }
        model.insertRow(target, entry);
        table.getSelectionModel().setSelectionInterval(target, target);
    }
}
