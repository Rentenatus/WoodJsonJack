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
 * Detail-Dialog fuer ein einzelnes Attribut eines {@code EditNode}. Zeigt oben
 * ein Label-Textfeld-Gitter mit Knoten-Name, Knoten-Pfad, Attribut-Typ und
 * Attribut-Name, darunter eine Beschriftung fuer den Attribut-Wert gefolgt von
 * einer zunaechst nicht editierbaren TextArea mit Umbruch und Scrollleisten.
 * Unten rechts befindet sich ein Close-Button.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackAttrDetailDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private JTextArea valueArea;

    /**
     * Erzeugt einen neuen modalen Detail-Dialog.
     *
     * @param owner das uebergeordnete Fenster
     * @param nodeName der Name des ausgewaehlten Knotens
     * @param nodePath der Pfad des ausgewaehlten Knotens
     * @param attrType der Typ des Attributs
     * @param attrName der Name des Attributs
     * @param attrValue der Wert des Attributs
     * @param editable ob das Attribut (und damit die TextArea) editierbar ist
     * @param acceptHandler Callback, der beim Klick auf "Accept" mit dem
     * bearbeiteten Wert aufgerufen wird; darf null sein, wenn nicht editierbar
     */
    public JsonJackAttrDetailDialog(Frame owner, String nodeName, String nodePath,
            String attrType, String attrName, String attrValue, boolean editable,
            Consumer<String> acceptHandler) {
        super(owner, "Attribut-Detail", true);
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

        addInfoRow(top, label, field, 0, "Knoten-Name:", nodeName);
        addInfoRow(top, label, field, 1, "Knoten-Pfad:", nodePath);
        addInfoRow(top, label, field, 2, "Attribut-Typ:", attrType);
        addInfoRow(top, label, field, 3, "Attribut-Name:", attrName);

        GridBagConstraints valueLabel = new GridBagConstraints();
        valueLabel.gridx = 0;
        valueLabel.gridy = 4;
        valueLabel.gridwidth = 2;
        valueLabel.anchor = GridBagConstraints.WEST;
        valueLabel.insets = new Insets(10, 0, 2, 0);
        top.add(new JLabel("Attribut-Value:"), valueLabel);

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
