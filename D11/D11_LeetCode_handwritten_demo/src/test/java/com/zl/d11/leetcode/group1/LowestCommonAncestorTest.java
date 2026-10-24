package com.zl.d11.leetcode.group1;

import com.zl.d11.leetcode.model.TreeNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LowestCommonAncestorTest {

    private final LowestCommonAncestor solution = new LowestCommonAncestor();

    @Test
    void testEmptyTree() {
        assertNull(solution.lowestCommonAncestor(null, null, null));
    }

    @Test
    void testRootIsTarget() {
        TreeNode root = new TreeNode(1);
        TreeNode p = root;
        TreeNode q = new TreeNode(2);
        assertSame(root, solution.lowestCommonAncestor(root, p, q));
    }

    @Test
    void testNodesOnBothSides() {
        TreeNode left = new TreeNode(2);
        TreeNode right = new TreeNode(3);
        TreeNode root = new TreeNode(1, left, right);

        TreeNode result = solution.lowestCommonAncestor(root, left, right);
        assertSame(root, result);
    }

    @Test
    void testNodesOnSameSide() {
        TreeNode node4 = new TreeNode(4);
        TreeNode node2 = new TreeNode(2, node4, null);
        TreeNode root = new TreeNode(1, node2, new TreeNode(3));

        TreeNode result = solution.lowestCommonAncestor(root, node2, node4);
        assertSame(node2, result);
    }

    @Test
    void testAncestorDescendantRelation() {
        TreeNode node3 = new TreeNode(3);
        TreeNode node2 = new TreeNode(2, node3, null);
        TreeNode root = new TreeNode(1, node2, null);

        TreeNode result = solution.lowestCommonAncestor(root, node2, node3);
        assertSame(node2, result);
    }
}