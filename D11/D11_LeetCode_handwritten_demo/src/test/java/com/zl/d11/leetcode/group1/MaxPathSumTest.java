package com.zl.d11.leetcode.group1;

import com.zl.d11.leetcode.model.TreeNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MaxPathSumTest {

    private final MaxPathSum solution = new MaxPathSum();

    @Test
    void testSingleNegativeNode() {
        TreeNode root = new TreeNode(-3);
        assertEquals(-3, solution.maxPathSum(root));
    }

    @Test
    void testChainLeftTree() {
        // 链状左树：1 -> -2 -> 3
        TreeNode node3 = new TreeNode(3);
        TreeNode node2 = new TreeNode(-2, node3, null);
        TreeNode root = new TreeNode(1, node2, null);
        assertEquals(3, solution.maxPathSum(root));
    }

    @Test
    void testAllNegativeTree() {
        // 全负数链状树：-10 -> -9 -> -8 -> -7 -> -6
        TreeNode n6 = new TreeNode(-6);
        TreeNode n7 = new TreeNode(-7, n6, null);
        TreeNode n8 = new TreeNode(-8, n7, null);
        TreeNode n9 = new TreeNode(-9, n8, null);
        TreeNode root = new TreeNode(-10, n9, null);
        assertEquals(-6, solution.maxPathSum(root));
    }

    @Test
    void testNormalBinaryTree() {
        // [-10,9,20,null,null,15,7]
        TreeNode left = new TreeNode(9);
        TreeNode right = new TreeNode(20, new TreeNode(15), new TreeNode(7));
        TreeNode root = new TreeNode(-10, left, right);
        assertEquals(42, solution.maxPathSum(root));
    }

    @Test
    void testSingleNode() {
        TreeNode root = new TreeNode(5);
        assertEquals(5, solution.maxPathSum(root));
    }
}