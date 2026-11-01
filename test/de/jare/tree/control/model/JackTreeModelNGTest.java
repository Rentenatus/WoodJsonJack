package de.jare.tree.control.model;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import java.util.HashSet;
import java.util.Set;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Regression tests for the ancestor refresh: after a node edit the labels of its ancestors
 * change with them (an array property shows the live summary of its children), and JTree
 * recomputes the row bounds only for nodes it hears about - a stale parent keeps the old text
 * and the old width.
 *
 * @author Janusch Rentenatus
 */
public class JackTreeModelNGTest {

    /**
     * Refreshing the ancestor rendering notifies exactly the ancestors of the edited node that
     * carry a parent - the direct array parent here. The root has no parent, so DefaultTreeModel
     * fires no change event for it (its label aggregates no children). The edited node and its
     * subtree stay untouched.
     */
    @Test
    public void testRefreshAncestorRenderingNotifiesTheAncestorChain() throws Exception {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"values\": [ \"alpha\", \"beta\" ] }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        final JackTreeModel model = new JackTreeModel(tree);

        final Set<Object> notified = new HashSet<>();
        model.addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {
                if (e.getChildren() != null) {
                    for (Object child : e.getChildren()) {
                        notified.add(child);
                    }
                }
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
            }
        });

        assertTrue(tree.getRoot().getChildAt(0) instanceof EditNodeProperty);
        final EditNodeProperty values = (EditNodeProperty) tree.getRoot().getChildAt(0);
        assertTrue(values.getChildAt(0) instanceof EditNodeObject);
        final EditNodeObject row = (EditNodeObject) values.getChildAt(0);

        model.refreshAncestorRendering(row);

        final Object valuesNode = model.findNodeById(values.getEditId());
        final Object rootNode = model.findNodeById(tree.getRoot().getEditId());
        final Object rowNode = model.findNodeById(row.getEditId());

        assertTrue(notified.contains(valuesNode), "the direct parent is refreshed");
        assertFalse(notified.contains(rowNode), "the edited node itself is not refreshed");
        assertEquals(notified.size(), 1,
                "the root carries no parent, DefaultTreeModel fires no change event for it");
    }

    /**
     * A null node refreshes nothing; the direct parent of an edit one level below the root is
     * still notified.
     */
    @Test
    public void testRefreshAncestorRenderingOfNullIsSilent() {
        final EditTree tree = new EditTree("{\"root\"}");
        final JackTreeModel model = new JackTreeModel(tree);
        model.refreshAncestorRendering(null); // must not throw
    }
}
