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
 * the JackEditPopup summarizes all disabled add-menu points of one logic run into a
 * single "ui" fast log entry.
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
        assertEquals(uiEntries.size(), 1,
                "one logic run should produce exactly one ui entry, got: " + messages(uiEntries));
        assertEquals(uiEntries.get(0).getMessage(),
                "[soft] edit popup: disabled add menus: add node (editor is read-only); add annotation (editor is read-only)");
    }

    @Test
    public void testSoftAddDisabledWhenNoSelection() {
        System.out.println("testSoftAddDisabledWhenNoSelection");

        TestEditor editor = new TestEditor(false);
        master.setActiveEditor(editor, "test");
        master.getFastLog().clear("ui");
        master.fireSelection(null, editor, false);
        List<FastLogEntry> uiEntries = master.getFastLog().getEntries("ui");
        assertEquals(uiEntries.size(), 1,
                "one logic run should produce exactly one ui entry, got: " + messages(uiEntries));
        assertEquals(uiEntries.get(0).getMessage(),
                "[soft] edit popup: disabled add menus: add node (no node selected); add annotation (no node selected)");
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
        assertEquals(uiEntries.size(), 1,
                "one logic run should produce exactly one ui entry, got: " + messages(uiEntries));
        assertEquals(uiEntries.get(0).getMessage(),
                "[soft] edit popup: disabled add menus: add annotation (selected node cannot parent an annotation)");
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
            assertFalse(entry.getMessage().contains("add node ("),
                    "no add-node point expected in the summary, got: " + entry.getMessage());
        }
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
