package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.model.JackTreeModel;
import de.jare.debug.JsonDebugLevel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Model tests for the Fields pane: row construction from the selected object,
 * parent resolution and focus on a clicked field, and the three-tier combo
 * box candidate logic (descriptor, primitive fallback, none for unparsed
 * objects).
 *
 * @author Janusch Rentenatus
 */
public class JsonJackFieldTableModelNGTest {

    private static final String LINKED_JSON = "{\n"
            + "  \"_woodObjectId\": \"1\",\n"
            + "  \"target\": {\n"
            + "    \"_woodObjectId\": \"2\",\n"
            + "    \"name\": \"Target\"\n"
            + "  },\n"
            + "  \"a\": {\n"
            + "    \"_woodLink\": \"this::2\"\n"
            + "  },\n"
            + "  \"count\": 5\n"
            + "}\n";

    private static EditTree importTree() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(LINKED_JSON, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    /**
     * Minimal focus component: the model only reads the tree model from it.
     */
    private static TreeFocusComponent componentOf(EditTree tree) {
        final JackTreeModel treeModel = new JackTreeModel(tree);
        return new TreeFocusComponent() {
            @Override
            public JTree getTree() {
                return null;
            }

            @Override
            public JackTreeModel getModel() {
                return treeModel;
            }

            @Override
            public de.jare.tree.control.JackMasterControl getJackMaster() {
                return null;
            }
        };
    }

    private static EditNodeProperty findProperty(EditTree tree, String name) {
        for (int i = 0; i < tree.getRoot().getChildCount(); i++) {
            if (tree.getRoot().getChildAt(i) instanceof EditNodeProperty prop
                    && name.equals(prop.getName())) {
                return prop;
            }
        }
        return null;
    }

    @Test
    public void testObjectSelectionBuildsRows() throws Exception {
        EditTree tree = importTree();
        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertEquals(model.getRowCount(), 4, "jsonType header plus the three fields");
        assertEquals(model.getColumnCount(), 4, "Name, Value, Type, JsonType");
        assertEquals(model.getValueAt(0, 0), "jsonType");
        assertEquals(model.getValueAt(1, 0), "target");
        assertEquals(model.getValueAt(2, 0), "a");
        assertEquals(model.getValueAt(3, 0), "count");
        assertEquals(model.getFocusedRowIndex(), -1, "an object click focuses no field");
    }

    @Test
    public void testFieldClickShowsParentAndFocusesRow() throws Exception {
        EditTree tree = importTree();
        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);

        EditNodeProperty fieldA = findProperty(tree, "a");
        assertNotNull(fieldA);
        model.onNodeSelected(new DefaultMutableTreeNode(fieldA), null, false);

        assertSame(model.getCurrentObject(), tree.getRoot(),
                "a clicked field still shows the owning object");
        assertEquals(model.getFocusedRowIndex(), 2, "the 'a' row gets the focus, matched by edit id");
    }

    @Test
    public void testPrimitiveFallbackOffersNodeTypes() throws Exception {
        EditTree tree = importTree();
        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        EditNodeProperty count = findProperty(tree, "count");
        assertNotNull(count);
        assertEquals(count.getType(), JsonNodeType.LONG);
        assertTrue(model.isComboBoxCell(3, 1), "an unparsed primitive field offers the node type candidates");
        assertEquals(model.getComboBoxValues(3).length, 5);
    }

    @Test
    public void testUnparsedObjectFieldHasNoCandidates() throws Exception {
        EditTree tree = importTree();
        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertFalse(model.isComboBoxCell(2, 1),
                "an unparsed object field has no model knowledge - no combo box");
    }

    @Test
    public void testDescribedObjectFieldOffersInstances() throws Exception {
        EditTree tree = importTree();
        JsonModelDescriptor descriptor = new JsonModelDescriptor("sceneModel");
        JsonTypeDescriptor sceneType = new JsonTypeDescriptor("Scene");
        descriptor.addType(sceneType);
        findProperty(tree, "target").setJsonField(new JsonFieldDescriptor("target", "Scene"));
        EditNodeObject targetObject = (EditNodeObject) findProperty(tree, "target").getChildAt(0);
        targetObject.setJsonType(sceneType);

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.setJsonModelDescriptor(descriptor);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertEquals(model.getValueAt(1, 3), "Scene", "the JsonType column shows the declared model type");
        assertTrue(model.isComboBoxCell(1, 1), "a described object field offers its instances");
        Object[] values = model.getComboBoxValues(1);
        assertEquals(values.length, 1, "exactly one Scene instance exists in the tree");
        assertTrue(values[0].toString().contains("Scene"), "the candidate names its type: " + values[0]);
    }

    /**
     * Regression for the lost field focus: JTable clears the selection model while processing
     * fireTableDataChanged - AFTER the TableModelListener has run. Applying the focus on the event
     * queue (as the pane does) must survive every subsequent rebuild.
     */
    @Test
    public void testFocusedRowSelectionSurvivesModelRebuilds() throws Exception {
        EditTree tree = importTree();
        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);

        javax.swing.JTable table = new javax.swing.JTable(model);
        table.getModel().addTableModelListener(e -> javax.swing.SwingUtilities.invokeLater(() -> {
            final int row = model.getFocusedRowIndex();
            if (row >= 0 && row < table.getRowCount()) {
                table.getSelectionModel().setSelectionInterval(row, row);
            }
        }));

        EditNodeProperty fieldA = findProperty(tree, "a");
        assertNotNull(fieldA);
        model.onNodeSelected(new DefaultMutableTreeNode(fieldA), null, false);
        java.awt.EventQueue.invokeAndWait(() -> { });
        assertEquals(table.getSelectedRow(), 2, "the field row is selected after the click");

        // the cascade after a click rebuilds the model twice: content command and parse problems
        model.onCommand("EDIT_ANY", null);
        java.awt.EventQueue.invokeAndWait(() -> { });
        assertEquals(table.getSelectedRow(), 2, "the selection survives the content command rebuild");

        model.onParseProblems(null, null);
        java.awt.EventQueue.invokeAndWait(() -> { });
        assertEquals(table.getSelectedRow(), 2, "the selection survives the parse problems rebuild");

        model.refresh();
        java.awt.EventQueue.invokeAndWait(() -> { });
        assertEquals(table.getSelectedRow(), 2, "the selection survives a manual refresh");
    }
}
