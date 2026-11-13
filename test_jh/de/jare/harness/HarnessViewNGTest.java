/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.tree.ui.harness.HarnessViewPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Headless-safe test of the harness view (CONCEPT_Harness.md §3, §15): after a run the
 * tree model mirrors the task tree (root + children) and the selection protocol shows
 * the metrics-only calculator log.
 *
 * @author Janusch Rentenatus
 */
public class HarnessViewNGTest {

    @Test
    public void testViewMirrorsTaskTreeAfterRun() throws Exception {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "view test?");
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        HarnessViewPanel view = new HarnessViewPanel();
        view.bind(instance, engine);
        engine.run();
        view.refresh();

        DefaultMutableTreeNode viewRoot = (DefaultMutableTreeNode) viewTreeRoot(view);
        Assert.assertEquals(viewRoot.getChildCount(), 1, "root task visible in the tree");
        DefaultMutableTreeNode rootTask = (DefaultMutableTreeNode) viewRoot.getChildAt(0);
        Assert.assertEquals(((TaskData) rootTask.getUserObject()).getTitle(), "view test?");
        Assert.assertEquals(rootTask.getChildCount(), 1, "reasoning subtask visible in the tree");
        DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootTask.getChildAt(0);
        Assert.assertTrue(child.getUserObject() instanceof TaskData);
        Assert.assertEquals(((TaskData) child.getUserObject()).getTaskType(), "REASONING");
    }

    private static Object viewTreeRoot(HarnessViewPanel view) throws Exception {
        var f = HarnessViewPanel.class.getDeclaredField("rootNode");
        f.setAccessible(true);
        return f.get(view);
    }
}
