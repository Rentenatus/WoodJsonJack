/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui;

import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.fastlog.FastLogEntry;
import de.jare.tree.control.fastlog.FastLogType;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.model.JackTreeModel;
import java.util.List;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test class for the enablement logging of the soft/hard edit menus. Verifies that
 * the JackEditPopup writes an "ui" fast log entry explaining why the soft add items
 * stay disabled.
 *
 * @author Mistral Vibe
 * @author Janusch Rentenatus
 */
public class JackEditPopupEnablementNGTest {

    private JackMasterControl master;
    private JackEditPopup popup;

    private static class TestEditor implements TreeFocusComponent {

        private final boolean readonly;
        private final JTree tree = new JTree();

        TestEditor(boolean readonly) {
            this.readonly = readonly;
        }

        @Override
        public JTree getTree() {
            return tree;
        }

        @Override
        public JackTreeModel getModel() {
            return null;
        }

        @Override
        public JackMasterControl getJackMaster() {
            return null;
        }

        @Override
        public boolean isReadonly() {
            return readonly;
        }
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        master = new JackMasterControl();
        popup = new JackEditPopup(master);
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        master = null;
        popup = null;
    }

    @Test
    public void testUiTypeRegisteredWritable() {
        System.out.println("testUiTypeRegisteredWritable");

        FastLogType ui = master.getFastLog().getType("ui");
        assertNotNull(ui, "ui fast log type should be registered");
        assertTrue(ui.isWritable(), "ui fast log type should be writable");
    }

    @Test
    public void testSoftAddDisabledWhenReadonly() {
        System.out.println("testSoftAddDisabledWhenReadonly");

        master.setActiveEditor(new TestEditor(true), "test");
        List<FastLogEntry> uiEntries = master.getFastLog().getEntries("ui");
        assertTrue(containsMessage(uiEntries, "[soft] edit popup: add node disabled: editor is read-only"),
                "expected read-only reason for add node, got: " + messages(uiEntries));
        assertTrue(containsMessage(uiEntries, "[soft] edit popup: add annotation disabled: editor is read-only"),
                "expected read-only reason for add annotation, got: " + messages(uiEntries));
    }

    @Test
    public void testSoftAddDisabledWhenNoSelection() {
        System.out.println("testSoftAddDisabledWhenNoSelection");

        TestEditor editor = new TestEditor(false);
        master.setActiveEditor(editor, "test");
        master.getFastLog().clear("ui");
        master.fireSelection(null, editor, false);
        List<FastLogEntry> uiEntries = master.getFastLog().getEntries("ui");
        assertTrue(containsMessage(uiEntries, "[soft] edit popup: add node disabled: no node selected"),
                "expected no-selection reason for add node, got: " + messages(uiEntries));
        assertTrue(containsMessage(uiEntries, "[soft] edit popup: add annotation disabled: no node selected"),
                "expected no-selection reason for add annotation, got: " + messages(uiEntries));
    }

    @Test
    public void testSoftAddAnnotationDisabledWhenNodeCannotParentAnnotation() {
        System.out.println("testSoftAddAnnotationDisabledWhenNodeCannotParentAnnotation");

        TestEditor editor = new TestEditor(false);
        master.setActiveEditor(editor, "test");
        master.getFastLog().clear("ui");
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Object());
        master.fireSelection(node, editor, false);
        List<FastLogEntry> uiEntries = master.getFastLog().getEntries("ui");
        assertTrue(containsMessage(uiEntries,
                "[soft] edit popup: add annotation disabled: selected node cannot parent an annotation"),
                "expected cannot-parent reason for add annotation, got: " + messages(uiEntries));
        assertFalse(containsMessage(uiEntries, "[soft] edit popup: add node disabled"),
                "add node should be enabled with a node selected, got: " + messages(uiEntries));
    }

    @Test
    public void testSoftAddNodeEnabledNoDisabledLog() {
        System.out.println("testSoftAddNodeEnabledNoDisabledLog");

        TestEditor editor = new TestEditor(false);
        master.setActiveEditor(editor, "test");
        master.getFastLog().clear("ui");
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Object());
        master.fireSelection(node, editor, false);
        List<FastLogEntry> uiEntries = master.getFastLog().getEntries("ui");
        for (FastLogEntry entry : uiEntries) {
            assertFalse(entry.getMessage().contains("add node disabled"),
                    "no add-node-disabled entry expected, got: " + entry.getMessage());
        }
    }

    private static boolean containsMessage(List<FastLogEntry> entries, String expected) {
        for (FastLogEntry entry : entries) {
            if (expected.equals(entry.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static String messages(List<FastLogEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (FastLogEntry entry : entries) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(entry.getMessage());
        }
        return sb.toString();
    }

}
