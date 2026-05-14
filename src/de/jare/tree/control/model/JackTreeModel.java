/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.jare.tree.control.model;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 *
 * @author Administrator
 */
public class JackTreeModel extends DefaultTreeModel {

    private EditTree editTree;

    public JackTreeModel(String rootName) {
        this(new EditTree(new EditNodeObject("{" + rootName + "}")));
    }

    public JackTreeModel(EditTree editTree) {
        super(new DefaultMutableTreeNode(editTree.getRoot()));
    }

    public EditTree getEditTree() {
        return editTree;
    }

}
