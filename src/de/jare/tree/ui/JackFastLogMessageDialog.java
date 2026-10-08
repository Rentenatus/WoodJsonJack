/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.fastlog.FastLogEntry;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

/**
 * Modal dialog that shows the full message text of a {@link FastLogEntry}.
 * A label/text field grid at the top with number, id and timestamp, below that
 * a read-only text area with wrapping and scroll bars. A close button at the
 * bottom right, ESC closes the dialog.
 *
 * @author Janusch Rentenatus
 */
public class JackFastLogMessageDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /**
     * Creates a new modal dialog for a fast log entry.
     *
     * @param owner the parent window
     * @param entry the entry to show
     */
    public JackFastLogMessageDialog(Frame owner, FastLogEntry entry) {
        super(owner, "Fast Log Message", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi(entry);
        setSize(540, 420);
        setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void buildUi(FastLogEntry entry) {
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

        GridBagConstraints label = new GridBagConstraints();
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(4, 0, 4, 8);
        label.gridx = 0;
        label.fill = GridBagConstraints.NONE;

        GridBagConstraints field = new GridBagConstraints();
        field.anchor = GridBagConstraints.WEST;
        field.insets = new Insets(4, 0, 4, 0);
        field.gridx = 1;
        field.weightx = 1.0;
        field.fill = GridBagConstraints.HORIZONTAL;

        String timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.getTimestamp()),
                ZoneId.systemDefault()).format(TIME_FORMAT);
        addInfoRow(top, label, field, 0, "Nr:", String.valueOf(entry.getNr()));
        addInfoRow(top, label, field, 1, "Id:", entry.getTypeId());
        addInfoRow(top, label, field, 2, "Timestamp:", timestamp);

        GridBagConstraints valueLabel = new GridBagConstraints();
        valueLabel.gridx = 0;
        valueLabel.gridy = 3;
        valueLabel.gridwidth = 2;
        valueLabel.anchor = GridBagConstraints.WEST;
        valueLabel.insets = new Insets(10, 0, 2, 0);
        top.add(new JLabel("Message:"), valueLabel);

        add(top, BorderLayout.NORTH);

        JTextArea messageArea = new JTextArea(entry.getMessage() == null ? "" : entry.getMessage());
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setEditable(false);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonBar.add(closeBtn);
        add(buttonBar, BorderLayout.SOUTH);
    }

    private void addInfoRow(JPanel panel, GridBagConstraints label, GridBagConstraints field,
            int y, String labelText, String value) {
        label.gridy = y;
        field.gridy = y;
        panel.add(new JLabel(labelText), label);
        JTextField textField = new JTextField(value == null ? "" : value);
        textField.setEditable(false);
        panel.add(textField, field);
    }

}
