/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.jare.jsoncasted.editor.core;

/**
 *
 * @author Janusch Rentenatus
 */
public class AbstractEntry {

    /**
     * The ID of the affected node, or {@code -1} if not yet known.
     */
    public final long nodeId;

    /**
     * The leftRange of the affected node, or {@code -1} if not yet known.
     */
    public final long leftRange;

    /**
     * The timesRange of the affected node, or {@code Long.MIN_VALUE} if not yet
     * known.
     */
    public final long timesRange;

    /**
     * Creates a new abstract entry with the specified node ID, leftRange, and
     * timesRange. The node ID represents the unique identifier of the affected
     * node in the tree, while the leftRange and timesRange provide information
     * about the position and structure of the node within the tree.
     *
     *
     * @param nodeId the ID of the affected node, or {@code -1} if not yet known
     * @param leftRange the leftRange of the affected node, or {@code -1} if not
     * yet known
     * @param timesRange the timesRange of the affected node, or
     * {@code Long.MIN_VALUE} if not yet known
     */
    public AbstractEntry(long nodeId, long leftRange, long timesRange) {
        this.nodeId = nodeId;
        this.leftRange = leftRange;
        this.timesRange = timesRange;
    }
}
