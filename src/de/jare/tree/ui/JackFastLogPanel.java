/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.fastlog.FastLog;
import de.jare.tree.control.fastlog.FastLogEntry;
import de.jare.tree.control.fastlog.FastLogListener;
import de.jare.tree.control.fastlog.FastLogType;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Pane fuer den FastLog im unteren TabbedPane. Links eine Liste der
 * Fast-Log-Typen (mit "All" als Filter fuer alle Typen), rechts eine Tabelle
 * mit den Spalten Nr, Id und Message. Die Message-Spalte wird als TextArea
 * gerendert und auf {@link #MAX_VISIBLE_LINES} Zeilen gedeckelt; der volle Text
 * ist per Doppelklick (oder Enter) in einem Dialog mit Scrollleisten zu sehen.
 * <p>
 * Aenderungen am Log werden ueber {@link FastLogListener} empfangen und per
 * invokeLater auf den EDT verlagert; mehrere schnelle Schreibzugriffe werden zu
 * einem Tabellen-Update zusammengefasst.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class JackFastLogPanel extends JPanel implements FastLogListener {

    private static final FastLogType ALL_TYPES = new FastLogType("", "All", false);
    private static final int MAX_VISIBLE_LINES = 5;

    private final FastLog fastLog;
    private final JackFastLogTableModel tableModel;
    private final JTable table;
    private final JList<FastLogType> typeList;
    private final JCheckBox followBox;
    private boolean updateScheduled;

    /**
     * Erzeugt die Pane und registriert sie als Listener am FastLog des
     * Master-Control.
     *
     * @param master der Master-Control
     */
    public JackFastLogPanel(JackMasterControl master) {
        this.fastLog = master.getFastLog();
        setLayout(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JButton clearBtn = new JButton("Clear");
        followBox = new JCheckBox("Follow", true);
        toolbar.add(clearBtn);
        toolbar.add(followBox);
        add(toolbar, BorderLayout.NORTH);

        tableModel = new JackFastLogTableModel(fastLog);
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        TableColumn messageCol = table.getColumnModel().getColumn(2);
        messageCol.setCellRenderer(new TextAreaRenderer());
        messageCol.setPreferredWidth(600);

        DefaultListModel<FastLogType> typeListModel = new DefaultListModel<>();
        typeListModel.addElement(ALL_TYPES);
        for (FastLogType type : fastLog.getTypes()) {
            typeListModel.addElement(type);
        }
        typeList = new JList<>(typeListModel);
        typeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typeList.setSelectedIndex(0);
        typeList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            FastLogType selected = typeList.getSelectedValue();
            tableModel.setFilter(selected == null || selected == ALL_TYPES ? null : selected.getId());
            layoutRowHeights();
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(typeList), new JScrollPane(table));
        split.setDividerLocation(160);
        add(split, BorderLayout.CENTER);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openDetailDialogAt(e.getPoint());
                }
            }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "openFastLogDetail");
        table.getActionMap().put("openFastLogDetail", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int viewRow = table.getSelectedRow();
                if (viewRow >= 0) {
                    openDetailDialog(tableModel.getEntryAt(table.convertRowIndexToModel(viewRow)));
                }
            }
        });

        clearBtn.addActionListener(e -> {
            FastLogType selected = typeList.getSelectedValue();
            fastLog.clear(selected == null || selected == ALL_TYPES ? null : selected.getId());
        });

        table.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutRowHeights();
            }
        });

        fastLog.addFastLogListener(this);
        layoutRowHeights();
    }

    private void openDetailDialogAt(java.awt.Point point) {
        int viewRow = table.rowAtPoint(point);
        if (viewRow < 0) {
            return;
        }
        openDetailDialog(tableModel.getEntryAt(table.convertRowIndexToModel(viewRow)));
    }

    private void openDetailDialog(FastLogEntry entry) {
        if (entry == null) {
            return;
        }
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        JackFastLogMessageDialog dialog = new JackFastLogMessageDialog(owner, entry);
        dialog.setVisible(true);
    }

    @Override
    public void onEntryAdded(FastLogEntry entry) {
        scheduleRefresh();
    }

    @Override
    public void onCleared(String typeId) {
        scheduleRefresh();
    }

    /**
     * Fasst schnelle Schreibzugriffe zu einem Tabellen-Update auf dem EDT
     * zusammen.
     */
    private void scheduleRefresh() {
        if (updateScheduled) {
            return;
        }
        updateScheduled = true;
        SwingUtilities.invokeLater(() -> {
            updateScheduled = false;
            tableModel.reload();
            layoutRowHeights();
            scrollToEndIfFollow();
        });
    }

    private void scrollToEndIfFollow() {
        if (!followBox.isSelected()) {
            return;
        }
        int last = table.getRowCount() - 1;
        if (last >= 0) {
            table.scrollRectToVisible(table.getCellRect(last, 0, true));
        }
    }

    /**
     * Setzt die Zeilenhoehen anhand des Message-Texts: der Text wird umgebrochen
     * und die Hoehe auf {@link #MAX_VISIBLE_LINES} Zeilen gedeckelt, damit sehr
     * lange Meldungen die Tabelle nicht auseinanderziehen.
     */
    private void layoutRowHeights() {
        int messageWidth = table.getColumnModel().getColumn(2).getWidth();
        if (messageWidth <= 0) {
            return;
        }
        FontMetrics fm = table.getFontMetrics(table.getFont());
        int maxHeight = MAX_VISIBLE_LINES * fm.getHeight() + 4;
        int defaultHeight = table.getRowHeight();
        for (int row = 0; row < table.getRowCount(); row++) {
            Object value = tableModel.getValueAt(row, 2);
            JTextArea area = new JTextArea(value == null ? "" : value.toString());
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(table.getFont());
            area.setSize(messageWidth, Short.MAX_VALUE);
            int preferred = area.getPreferredSize().height;
            table.setRowHeight(row, Math.max(defaultHeight, Math.min(preferred, maxHeight)));
        }
    }

    /**
     * Renderer, der die Message-Spalte als nicht editierbare TextArea mit
     * Zeilenumbruch anzeigt.
     */
    private static class TextAreaRenderer extends javax.swing.JTextArea implements TableCellRenderer {

        TextAreaRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }
            return this;
        }
    }

}
