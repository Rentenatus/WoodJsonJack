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
import java.util.List;
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
        assertEquals(model.getColumnCount(), 5, "Name, Value, Type, JsonType, ^");
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

    @Test
    public void testAnnotationRowShowsAnnotationInJsonType() throws Exception {
        final String annotatedJson = "{\n"
                + "  \"@doc\": \"Hello\","
                + "  \"name\": \"Target\"\n"
                + "}\n";
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(annotatedJson, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertEquals(model.getRowCount(), 3, "jsonType header, the annotation and the field");
        assertTrue(model.getValueAt(1, 0).toString().startsWith("@"),
                "the annotation row shows its composite display name: " + model.getValueAt(1, 0));
        assertEquals(model.getValueAt(1, 3), "annotation", "the JsonType column marks the annotation row");
        assertEquals(model.getValueAt(1, 1), "Hello", "the annotation row shows its own primitive value");
    }

    @Test
    public void testPrimitiveRowGetsSyntheticValueRow() throws Exception {
        final String primitiveJson = "{\n"
                + "  \"values\": [ \"alpha\", \"beta\" ]\n"
                + "}\n";
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(primitiveJson, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");

        // the first array row is a primitive object
        EditNodeProperty values = findProperty(tree, "values");
        assertNotNull(values);
        assertTrue(values.getChildAt(0) instanceof EditNodeObject);
        EditNodeObject rowObject = (EditNodeObject) values.getChildAt(0);

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(rowObject), null, false);

        assertEquals(model.getRowCount(), 2, "jsonType header plus the synthetic value row");
        assertEquals(model.getValueAt(1, 0), "value");
        assertEquals(model.getValueAt(1, 1), "alpha");
        assertTrue(model.isCellEditable(1, 1), "the synthetic value row is editable");

        model.setValueAt("gamma", 1, 1);
        assertEquals(model.getValueAt(1, 1), "gamma");
        assertEquals(rowObject.getValue(), "gamma", "editing the synthetic row writes the object value");
    }

    @Test
    public void testPrimitiveArrayIsNotInlineEditable() throws Exception {
        EditTree tree = importTree();

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        // the "count" row carries primitive fallback combo candidates: not inline editable, like every combo cell
        assertFalse(model.isCellEditable(3, 1), "combo cells (primitive fallback) are not inline editable");

        // the "values" row is an array field
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"values\": [ \"alpha\", \"beta\" ] }\n", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree listTree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        JsonJackFieldTableModel listModel = new JsonJackFieldTableModel();
        listModel.onEditorSelected(componentOf(listTree), null);
        listModel.onNodeSelected(new DefaultMutableTreeNode(listTree.getRoot()), null, false);

        assertTrue(listModel.isArrayFieldRow(1), "an array field row is detected");
        assertTrue(listModel.isPrimitiveArrayListRow(1), "a list of primitives is a primitive list");
        assertFalse(listModel.isCellEditable(1, 1), "array fields are not edited inline");
        assertEquals(listModel.getCurrentPrimitiveList(1), java.util.List.of("alpha", "beta"));
    }

    @Test
    public void testDetailButtonEnablement() throws Exception {
        EditTree tree = importTree();
        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertFalse(model.isButtonEnabled(0), "the jsonType header opens nothing");
        assertTrue(model.isButtonEnabled(1), "an object field with a value opens the detail dialog");
        assertTrue(model.isButtonEnabled(3), "a primitive with a value opens the detail dialog");

        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"values\": [ \"alpha\", \"beta\" ] }\n", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree listTree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        JsonJackFieldTableModel listModel = new JsonJackFieldTableModel();
        listModel.onEditorSelected(componentOf(listTree), null);
        listModel.onNodeSelected(new DefaultMutableTreeNode(listTree.getRoot()), null, false);

        assertTrue(listModel.isButtonEnabled(1), "an array field opens the list editor");
    }

    @Test
    public void testDetailButtonDisabledWithoutValue() throws Exception {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"text\": \"\" }\n", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertEquals(model.getRowCount(), 2, "jsonType header plus the empty text field");
        assertFalse(model.isButtonEnabled(1), "a field without value opens nothing");
    }

    @Test
    public void testApplyPrimitiveListResult() throws Exception {
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"values\": [ \"alpha\", \"beta\" ] }\n", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        model.applyPrimitiveListResult(1, java.util.List.of("x", "y", "z"));

        assertEquals(model.getCurrentPrimitiveList(1), java.util.List.of("x", "y", "z"),
                "the list editor result replaces the values");
        de.jare.jsoncasted.editor.core.EditNodeObject row = (de.jare.jsoncasted.editor.core.EditNodeObject)
                model.fieldOfRow(1).getChildAt(1);
        assertEquals(row.getValue(), "y");
    }

    @Test
    public void testObjectListCollectionDataAndApply() throws Exception {
        final String sceneJson = "{\n"
                + "  \"scenes\": [ { \"_woodObjectId\": \"4\", \"name\": \"A\" } ],\n"
                + "  \"spare\": { \"_woodObjectId\": \"5\", \"name\": \"B\" }\n"
                + "}\n";
        de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(sceneJson, JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");

        JsonModelDescriptor descriptor = new JsonModelDescriptor("sceneModel");
        JsonTypeDescriptor sceneType = new JsonTypeDescriptor("Scene");
        descriptor.addType(sceneType);

        EditNodeProperty scenesField = findProperty(tree, "scenes");
        EditNodeProperty spareField = findProperty(tree, "spare");
        assertNotNull(scenesField);
        assertNotNull(spareField);
        scenesField.setJsonField(new JsonFieldDescriptor("scenes", "Scene"));
        assertTrue(scenesField.getChildAt(0) instanceof EditNodeObject);
        assertTrue(spareField.getChildAt(0) instanceof EditNodeObject);
        final EditNodeObject inList = (EditNodeObject) scenesField.getChildAt(0);
        final EditNodeObject spare = (EditNodeObject) spareField.getChildAt(0);
        inList.setJsonType(sceneType);
        spare.setJsonType(sceneType);

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.setJsonModelDescriptor(descriptor);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertTrue(model.isArrayFieldRow(1), "the scenes row is an array field");
        assertFalse(model.isPrimitiveArrayListRow(1), "a list of objects is no primitive list");
        assertFalse(model.isCellEditable(1, 1), "object lists are not edited inline");

        final List<JsonJackFieldTableModel.InstanceCandidate> candidates = model.getInstanceCandidates(1);
        assertEquals(candidates.size(), 2, "both Scene instances fit the declared element type");

        final List<JsonJackFieldTableModel.InstanceCandidate> current = model.getCurrentObjectList(1);
        assertEquals(current.size(), 1, "one element is in the list");
        assertSame(current.get(0).node(), inList);

        // accept: the spare instance first (becomes a reference stub), the own element second (kept by identity)
        JsonJackFieldTableModel.InstanceCandidate spareCandidate = null;
        for (JsonJackFieldTableModel.InstanceCandidate candidate : candidates) {
            if (candidate.node() == spare) {
                spareCandidate = candidate;
            }
        }
        assertNotNull(spareCandidate);
        model.applyObjectListResult(1, java.util.List.of(spareCandidate, current.get(0)));

        final EditNodeProperty fieldAfter = model.fieldOfRow(1);
        assertEquals(fieldAfter.getChildCount(), 2, "both chosen elements are in the list");
        final EditNodeObject first = (EditNodeObject) fieldAfter.getChildAt(0);
        final EditNodeObject second = (EditNodeObject) fieldAfter.getChildAt(1);
        assertTrue(first.isLinkReference(), "the added instance becomes a reference stub");
        assertEquals(first.getLinkTarget(), "this::5");
        assertSame(second, inList, "the kept element is the original node");
        assertTrue(tree.getLinkingSet().hasReferencesTo("this::5"),
                "the stub is registered in the reverse index");
    }

    @Test
    public void testCandidatesAndValueColumnUseEditString() throws Exception {
        EditTree tree = importTree();
        JsonModelDescriptor descriptor = new JsonModelDescriptor("sceneModel");
        JsonTypeDescriptor sceneType = new JsonTypeDescriptor("Scene");
        sceneType.addAnnotation(de.jare.jsoncasted.model.item.JsonAnnotation.editString("${name} #${woodId}"));
        descriptor.addType(sceneType);
        findProperty(tree, "target").setJsonField(new JsonFieldDescriptor("target", "Scene"));
        EditNodeObject targetObject = (EditNodeObject) findProperty(tree, "target").getChildAt(0);
        targetObject.setJsonType(sceneType);

        JsonJackFieldTableModel model = new JsonJackFieldTableModel();
        model.onEditorSelected(componentOf(tree), null);
        model.setJsonModelDescriptor(descriptor);
        model.onNodeSelected(new DefaultMutableTreeNode(tree.getRoot()), null, false);

        assertEquals(model.getValueAt(1, 1), "Target #2",
                "the value column shows the formatted editString of the child object");
        assertTrue(model.isComboBoxCell(1, 1), "a described object field offers its instances");
        Object[] values = model.getComboBoxValues(1);
        assertEquals(values.length, 1, "exactly one Scene instance exists in the tree");
        assertTrue(values[0].toString().contains("Target #2"),
                "the candidate label shows the formatted editString: " + values[0]);
    }

    @Test
    public void testCandidatesFallBackToPlainNamesWithoutTemplate() throws Exception {
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

        assertEquals(model.getValueAt(1, 1), "Target",
                "without a template the value column falls back to the object name");
        Object[] values = model.getComboBoxValues(1);
        assertTrue(values[0].toString().startsWith("Target"),
                "without a template the candidate label falls back to the object name: " + values[0]);
    }
}
