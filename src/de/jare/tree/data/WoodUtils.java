/*
 */
package de.jare.tree.data;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 *
 * @author Janusch Rentenatus
 */
public class WoodUtils {

    public static DefaultMutableTreeNode[] sortOnPos(DefaultMutableTreeNode[] nodesOrg) {
        int sortPos = 0;
        DefaultMutableTreeNode[] nodes = new DefaultMutableTreeNode[nodesOrg.length];
        int nextIdx = 0;
        while (nextIdx < nodesOrg.length) {
            int nextPos = Integer.MAX_VALUE;
            for (int i = 0; i < nodesOrg.length; i++) {
                DefaultMutableTreeNode next = nodesOrg[i];
                if (next.getParent() == null || next.getParent().getIndex(next) == sortPos) {
                    nodes[nextIdx] = next;
                    nextIdx++;
                } else {
                    int check = next.getParent().getIndex(next);
                    if (check > sortPos && check < nextPos) {
                        nextPos = check;
                    }
                }
            }
            sortPos = nextPos;
        }
        return nodes;
    }
}
