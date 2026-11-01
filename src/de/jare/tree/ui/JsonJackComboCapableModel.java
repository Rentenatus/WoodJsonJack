package de.jare.tree.ui;

/**
 * Combo box support of a table model: a model implementing this interface can
 * mark value cells as combo box cells and provide their candidate values. The
 * shared cell editor builds its combo boxes from these two methods, so the
 * Attributes and the Fields pane use the same editor.
 *
 * @author Janusch Rentenatus
 */
public interface JsonJackComboCapableModel {

    /**
     * Checks whether the cell should be rendered and edited as a combo box.
     *
     * @param rowIndex the row index
     * @param columnIndex the column index
     * @return true when the cell is a combo box cell
     */
    boolean isComboBoxCell(int rowIndex, int columnIndex);

    /**
     * Returns the candidate values of a combo box cell.
     *
     * @param rowIndex the row index
     * @return the values, never null
     */
    Object[] getComboBoxValues(int rowIndex);
}
