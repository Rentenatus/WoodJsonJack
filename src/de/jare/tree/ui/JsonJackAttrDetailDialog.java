/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

/**
 * Detail dialog for a single attribute of an {@code EditNode}. Shows a
 * label/text field grid at the top with node name, node path, attribute type and
 * attribute name, below that a caption for the attribute value followed by
 * a text area that is initially read-only, with line wrapping and scroll bars.
 * A close button sits at the bottom right.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackAttrDetailDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private JTextArea valueArea;

    /**
     * Creates a new modal detail dialog.
     *
     * @param owner the parent window
     * @param nodeName the name of the selected node
     * @param nodePath the path of the selected node
     * @param attrType the type of the attribute
     * @param attrName the name of the attribute
     * @param attrValue the value of the attribute
     * @param editable whether the attribute (and thus the text area) is editable
     * @param acceptHandler callback invoked with the edited value when "Accept"
     * is clicked; may be null when not editable
     */
    public JsonJackAttrDetailDialog(Frame owner, String nodeName, String nodePath,
            String attrType, String attrName, String attrValue, boolean editable,
            Consumer<String> acceptHandler) {
        super(owner, "Attribute Detail", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi(nodeName, nodePath, attrType, attrName, attrValue, editable, acceptHandler);
        setSize(540, 420);
        setLocationRelativeTo(owner);
    }

    private void buildUi(String nodeName, String nodePath, String attrType,
            String attrName, String attrValue, boolean editable,
            Consumer<String> acceptHandler) {
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 4, 8));

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

        addInfoRow(top, label, field, 0, "Node Name:", nodeName);
        addInfoRow(top, label, field, 1, "Node Path:", nodePath);
        addInfoRow(top, label, field, 2, "Attribute Type:", attrType);
        addInfoRow(top, label, field, 3, "Attribute Name:", attrName);

        GridBagConstraints valueLabel = new GridBagConstraints();
        valueLabel.gridx = 0;
        valueLabel.gridy = 4;
        valueLabel.gridwidth = 2;
        valueLabel.anchor = GridBagConstraints.WEST;
        valueLabel.insets = new Insets(10, 0, 2, 0);
        top.add(new JLabel("Attribute Value:"), valueLabel);

        add(top, BorderLayout.NORTH);

        valueArea = new JTextArea(attrValue == null ? "" : attrValue);
        valueArea.setLineWrap(true);
        valueArea.setWrapStyleWord(true);
        valueArea.setEditable(editable);
        JScrollPane scroll = new JScrollPane(valueArea);
        add(scroll, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton acceptBtn = new JButton("Accept");
        acceptBtn.setEnabled(editable);
        acceptBtn.addActionListener(e -> {
            if (acceptHandler != null) {
                acceptHandler.accept(valueArea.getText());
            }
            dispose();
        });
        buttonBar.add(acceptBtn);
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
