/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a JSON object node in the tree structure. Based on JsonObjectData
 * with tree structure support added.
 */
public abstract non-sealed class EditNodeAbstract implements EditNode {

    public final static long LEFT = Long.MIN_VALUE + 1;
    public final static long RIGHT = Long.MAX_VALUE - 1;

    private final long editId;
    private long leftRange;
    private long rightRange;
    private String primValue;
    private EditNodeAbstract parent;
    private final List<EditNodeAbstract> children = new ArrayList<>();
    private final List<EditNodeAbstract> sortedChildren = new ArrayList<>();
    private int cachedWeight;

    private final Object weightMonitor = new Object();

    public EditNodeAbstract(String objektInfo) {
        this.editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        this.leftRange = LEFT;
        this.rightRange = RIGHT;
        this.primValue = null;
        this.cachedWeight = 1;
    }

    public EditNodeAbstract(long editId, String primValue, String objektInfo) {
        this.editId = editId;
        this.leftRange = LEFT;
        this.rightRange = RIGHT;
        this.primValue = primValue;
        this.cachedWeight = 1;
    }

    EditNodeAbstract(long editId, long leftRange, long rightRange, String primValue, String objektInfo) {
        this.editId = editId;
        this.leftRange = leftRange;
        this.rightRange = rightRange;
        this.primValue = primValue;
        this.cachedWeight = 1;
    }

    @Override
    public long getEditId() {
        return editId;
    }

    @Override
    public long getLeftRange() {
        return leftRange;
    }

    public void setLeftRange(long leftRange) {
        synchronized (weightMonitor) {
            this.leftRange = leftRange;
        }
    }

    @Override
    public long getRightRange() {
        return rightRange;
    }

    public void setRightRange(long rightRange) {
        synchronized (weightMonitor) {
            this.rightRange = rightRange;
        }
    }

    // ========== Tree structure methods ==========
    @Override
    public String getValue() {
        return primValue;
    }

    @Override
    public void setValue(String value) {
        this.primValue = value;
    }

    @Override
    public EditNodeAbstract getParent() {
        return parent;
    }

    void setParent(EditNodeAbstract parent) {
        this.parent = parent;
    }

    @Override
    public List<EditNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    List<EditNodeAbstract> getAbstractChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public int getWeight() {
        int weight = 1;
        synchronized (weightMonitor) {
            for (EditNodeAbstract child : children) {
                weight += child.getWeight();
            }
            return cachedWeight = weight;
        }
    }

    @Override
    public int getCachedWeight() {
        return cachedWeight;
    }

    @Override
    public EditNodeAbstract getChildAt(int index) {
        return children.get(index);
    }

    @Override
    public int getChildIndex(EditNode child) {
        return children.indexOf(child);
    }

    public boolean isRangeConsistent() {
        // Pruefe gegen sortedChildren (nach Range sortiert)
        for (int i = 0; i < sortedChildren.size(); i++) {
            EditNodeAbstract child = sortedChildren.get(i);
            // Kind muss innerhalb des Eltern-Ranges liegen
            if (child.getLeftRange() < this.leftRange || child.getRightRange() > this.rightRange) {
                return false;
            }
            // In sortierter Liste: Keine Ueberlappung wenn rightRange <= next.leftRange
            if (i < sortedChildren.size() - 1) {
                EditNodeAbstract next = sortedChildren.get(i + 1);
                if (child.getRightRange() > next.getLeftRange()) {
                    return false;
                }
            }
        }
        return true;
    }

    void addChild(EditNodeAbstract child) {
        addChild(child, children.size());
    }

    void addChild(EditNodeAbstract child, int index) {
        if (child == null) {
            throw new IllegalArgumentException("Child cannot be null");
        }
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        EditNodeAbstract oldParent = child.getParent();
        if (oldParent != null && oldParent != this) {
            oldParent.removeChild(child);
        }
        children.add(index, child);
        child.setParent(this);

        synchronized (weightMonitor) {
            // Finde den groessten freien Intervall in sortedChildren
            long maxGapStart = this.leftRange;
            long maxGapSize = 0;
            EditNodeAbstract current;

            // Pruefe Intervall vor dem ersten Kind
            if (sortedChildren.isEmpty()) {
                // Keine Kinder, ganzer Eltern-Range ist frei
                maxGapSize = this.rightRange - this.leftRange + 1;
                setNextFreeRangeTo(child, this.leftRange, maxGapSize);
                sortedChildren.add(child);
                return;
            } else {
                current = sortedChildren.get(0);
                long gapSize = current.getLeftRange() - this.leftRange;
                if (gapSize > maxGapSize) {
                    maxGapSize = gapSize;
                    maxGapStart = this.leftRange;
                }
            }
            int sortedIndex = 0;

            // Pruefe Intervalle zwischen den Kindern
            for (int i = 1; i < sortedChildren.size(); i++) {
                EditNodeAbstract next = sortedChildren.get(i);
                long gapSize = next.getLeftRange() - current.getRightRange() - 1;
                if (gapSize > maxGapSize) {
                    maxGapSize = gapSize;
                    maxGapStart = current.getRightRange() + 1;
                    sortedIndex = i;
                }
                current = next;
            }

            // Pruefe Intervall nach dem letzten Kind
            long gapSize = this.rightRange - current.getRightRange() - 1;
            if (gapSize > maxGapSize) {
                maxGapSize = gapSize;
                maxGapStart = current.getRightRange() + 1;
                sortedIndex = sortedChildren.size();
            }

            setNextFreeRangeTo(child, maxGapStart, maxGapSize);
            sortedChildren.add(sortedIndex, child);
        }
    }

    private long setNextFreeRangeTo(EditNodeAbstract child, long gapStart, long maxGapSize) {
        int weight = child.getWeight();
        // Use 25% of the largest available interval on the left side—at least `weight * 2`,
        //  but no more than the available amount.
        long rangeSize = Math.max(0, Math.min(maxGapSize, Math.max(weight + weight, maxGapSize / 4)));
        child.setLeftRange(gapStart);
        child.setRightRange(gapStart + rangeSize);
        child.rangeRelabelingFor(weight - 1);
        return gapStart;
    }

    public void rangeRelabeling() {
        if (sortedChildren.isEmpty()) {
            return;
        }
        int totalWeight = 0;
        synchronized (weightMonitor) {
            for (EditNodeAbstract child : sortedChildren) {
                int weight = child.getWeight();
                totalWeight += weight;
            }
            rangeRelabelingFor(totalWeight);
        }
    }

    private void rangeRelabelingFor(int totalWeight) {
        // Mindestes 75.0% verteilen
        double availableRange = this.rightRange - this.leftRange + 1; // inklusiv
        availableRange = availableRange * Math.max(0.75d, totalWeight / availableRange);

        final int size = sortedChildren.size();
        // Verteile anteilig an Gewichten chronologisch
        long currentStart = this.leftRange;
        for (int i = 0; i < size; i++) {
            EditNodeAbstract child = sortedChildren.get(i);
            int weight = child.getCachedWeight();
            double weightRatio = weight / totalWeight;
            long rangeSize = (long) Math.round(weightRatio * availableRange);
            child.setLeftRange(currentStart);
            child.setRightRange(Math.min(this.rightRange, currentStart + rangeSize - 1)); // inklusiv
            currentStart = Math.min(this.rightRange, currentStart + rangeSize);
            child.rangeRelabelingFor(weight - 1);
        }
    }

    public boolean removeChild(EditNodeAbstract child) {
        boolean removed = children.remove(child);
        if (removed) {
            synchronized (weightMonitor) {
                sortedChildren.remove(child);
                child.setParent(null);
                // Notify child that it was removed
                child.sayOnRemoved(this);
            }
        }
        return removed;
    }

    public abstract EditNodeAbstract deepCopy(boolean regenerateEditId);

    public EditNodeAbstract deepCopy() {
        return deepCopy(true);
    }

    /**
     * Called when this node is removed from its parent node, giving the parent
     * a chance to update its internal state.
     */
    abstract void sayOnRemoved(EditNode parent);

    // ========== Factory methods ==========
    abstract EditNodeAbstract addNewChild(String aName);

    abstract EditNodeAbstract addNewChild(String aName, int index);

    public abstract EditNodeAbstract createChild(String aName);

    public abstract EditNodeAbstract createNeighbor(String aName);

}
