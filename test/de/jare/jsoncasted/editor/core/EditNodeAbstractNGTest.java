package de.jare.jsoncasted.editor.core;

import java.lang.reflect.Constructor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author jRent
 */
public class EditNodeAbstractNGTest {

    public EditNodeAbstractNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start EditNodeAbstractNGTest.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End EditNodeAbstractNGTest.");
        System.out.println("===============================================");
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
    }

    @Test
    public void testReflectionConstructorAndTreeOperations() throws Exception {
        // Create root node using reflection for private constructor
        long editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        Constructor<EditNodeObject> nodeConstructor = EditNodeObject.class.getDeclaredConstructor(
                long.class, long.class, long.class, String.class, String.class);
        nodeConstructor.setAccessible(true);
        EditNodeObject root = nodeConstructor.newInstance(editId, 10L, 19L, "rootValue", "rootObject");

        // Create tree with the root using reflection for package-private constructor
        Constructor<EditTree> treeConstructor = EditTree.class.getDeclaredConstructor(EditNodeAbstract.class);
        treeConstructor.setAccessible(true);
        EditTree tree = treeConstructor.newInstance(root);

        // Add 6 children to root via tree
        for (int i = 1; i <= 6; i++) {
            tree.addNewChild(root, "child" + i);
        }

        // Print tree
        System.out.println("\n--- Tree after adding 6 children ---");
        printTree(root, "");

        EditNodeAbstract child3 = root.getChildAt(3);

        // Make deep copy
        EditNodeAbstract copy = root.deepCopy();

        // Add copy to root
        tree.addChild(child3, copy);

        // Print everything again
        System.out.println("\n--- Tree after adding deep copy ---");
        printTree(root, "");
    }

    private void printTree(EditNode node, String indent) {
        System.out.println(indent + node.getClass().getSimpleName()
                + "[editId=" + node.getEditId()
                + ", leftRange=" + node.getLeftRange()
                + ", rightRange=" + node.getRightRange()
                + ", name=" + node.getName()
                + ", value=" + node.getValue()
                + ", type=" + node.getTypeKey() + "]");
        for (int i = 0; i < node.getChildCount(); i++) {
            printTree(node.getChildAt(i), indent + "  ");
        }
    }
}
