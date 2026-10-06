/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.parse;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Tests the annotation handling of the on-the-fly parser against the annotated soft parse assets: declared
 * annotations bind OKAY, undeclared ones are tolerated free annotations with a WARNING (never an ERROR), composite
 * keys resolve to children of their target field node, annotation rows adopt the implicit String type, and the
 * deletion of a field never silently removes an annotation.
 *
 * @author Janusch Rentenatus
 */
public class AnnotationSoftParseNGTest {

    /**
     * The binding matrix on the annotated template: every node is OKAY except the undeclared annotations, which
     * carry a tolerated WARNING. The declared object annotation and the declared field annotation bind OKAY,
     * composite keys land under their target field node, and no node is ever in ERROR state.
     */
    @Test
    public void testAnnotationBindingMatrix() throws Exception {
        final EditTree tree = loadAnnotatedTemplate();

        final List<EditNodeAbstract> notOkay = collectNotOkay(tree.getRoot(), new ArrayList<>());
        for (final EditNodeAbstract node : notOkay) {
            assertTrue(node instanceof EditNodeProperty && node.getName().startsWith("@"),
                    "Only annotations may carry a problem: " + describe(node));
            assertEquals(node.getEditStatus(), EditStatus.WARNING,
                    "Undeclared annotations must be tolerated warnings: " + describe(node));
        }

        final EditNodeObject root = (EditNodeObject) tree.getRoot();
        final EditNodeAbstract hint = findChild(root, "@hint");
        assertNotNull(hint, "The declared object annotation must exist");
        assertEquals(hint.getEditStatus(), EditStatus.OKAY,
                "The declared object annotation must bind OKAY: " + describe(hint));
        assertEquals(hint.getChildCount(), 2, "The hint annotation carries its two rows");
        for (int i = 0; i < hint.getChildCount(); i++) {
            final EditNodeObject row = (EditNodeObject) hint.getChildAt(i);
            assertEquals(row.getCastName(), "String",
                    "Annotation rows must adopt the implicit String type: " + describe(row));
            assertEquals(row.getEditStatus(), EditStatus.OKAY,
                    "Annotation rows must parse OKAY: " + describe(row));
        }

        final EditNodeAbstract dok = findChild(root, "@dok");
        assertNotNull(dok, "The undeclared object annotation must exist");
        assertEquals(dok.getEditStatus(), EditStatus.WARNING,
                "An undeclared annotation must be a tolerated warning, not an error: " + describe(dok));
        assertTrue(dok.getEditMessage() != null && dok.getEditMessage().contains("not declared"),
                "The warning must name the reason: " + describe(dok));

        final EditNodeAbstract comments = findChild(root, "comments");
        assertNotNull(comments, "The comments field node must exist");
        final EditNodeAbstract doc = findChild(comments, "@doc");
        assertNotNull(doc, "The composite annotation must be a child of its target field node");
        assertSame(doc.getParent(), comments, "Kind von comments - the annotation lives under the field node");
        assertEquals(doc.getEditStatus(), EditStatus.OKAY,
                "The declared field annotation must bind OKAY: " + describe(doc));

        final EditNodeAbstract mainLogging = findChild(root, "mainLogging");
        final EditNodeAbstract review = findChild(mainLogging, "@review");
        assertNotNull(review, "The composite review annotation must bind under mainLogging");
        assertEquals(review.getEditStatus(), EditStatus.WARNING,
                "An annotation that is not declared for that field is tolerated: " + describe(review));
    }

    /**
     * Deleting the annotated field never silently removes the annotation: it falls back to the owning object with
     * its composite anchor and a WARNING, exactly as the annotation concept demands for persistent annotations.
     */
    @Test
    public void testFieldDeletionRescuesTheAnnotation() throws Exception {
        final EditTree tree = loadAnnotatedTemplate();
        final EditNodeObject root = (EditNodeObject) tree.getRoot();
        final EditNodeAbstract comments = findChild(root, "comments");
        assertNotNull(comments, "The comments field node must exist before the deletion");
        final EditNodeAbstract doc = findChild(comments, "@doc");
        assertNotNull(doc, "The field annotation must live under the comments node before the deletion");

        assertTrue(tree.removeNode(comments), "The comments node must be removable");
        waitForParser(tree);

        final EditNodeAbstract rescued = findChild(root, "@doc:comments");
        assertNotNull(rescued,
                "The annotation must survive the field deletion and stay anchored at the object");
        assertSame(rescued, doc, "The rescued annotation must be the very same node, never a lost copy");
        assertSame(rescued.getParent(), root, "The rescued annotation is anchored at the owning object");
        assertEquals(rescued.getEditStatus(), EditStatus.WARNING,
                "The rescued annotation falls back to a WARNING: " + describe(rescued));
        assertTrue(rescued.getEditMessage() != null && rescued.getEditMessage().contains("missing"),
                "The warning must name the missing target field: " + describe(rescued));
    }

    // ========== Helpers ==========

    private EditTree loadAnnotatedTemplate() throws Exception {
        final File template = findAsset("ParseConfigAnnotated.json");
        final EditTree tree = JsonTreeConverter.fromJsonFile(template);
        assertNotNull(tree, "The annotated template must load into an edit tree");
        waitForParser(tree);
        return tree;
    }

    private File findAsset(String fileName) {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 4 && dir != null; i++) {
            File candidate = new File(dir, "test_assets/assets/parse/" + fileName);
            if (candidate.exists()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        throw new AssertionError("Test asset not found: test_assets/assets/parse/" + fileName
                + " (working dir: " + System.getProperty("user.dir") + ")");
    }

    private static EditNodeAbstract findChild(EditNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i).getName().equals(name)) {
                return (EditNodeAbstract) parent.getChildAt(i);
            }
        }
        return null;
    }

    private static List<EditNodeAbstract> collectNotOkay(EditNode node, List<EditNodeAbstract> out) {
        if (node instanceof EditNodeAbstract abs && abs.getEditStatus() != EditStatus.OKAY) {
            out.add(abs);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNotOkay(node.getChildAt(i), out);
        }
        return out;
    }

    private static String describe(EditNodeAbstract node) {
        if (node == null) {
            return "null";
        }
        return node.getName() + " -> " + node.getEditStatus() + ": " + node.getEditMessage();
    }

    private static void waitForParser(EditTree tree) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                Thread.sleep(150);
                if (tree.getParseQueue().isEmpty() && tree.getPendingNodes().isEmpty()) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Parser did not settle within the timeout");
    }
}
