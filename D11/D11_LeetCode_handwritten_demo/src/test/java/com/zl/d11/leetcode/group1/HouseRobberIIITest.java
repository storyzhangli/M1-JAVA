package com.zl.d11.leetcode.group1;

import com.zl.d11.leetcode.model.TreeNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseRobberIIITest {

    private final HouseRobberIII solution = new HouseRobberIII();

    @Test
    void testEmptyTree() {
        assertEquals(0, solution.rob(null));
    }

    @Test
    void testSingleNode() {
        assertEquals(5, solution.rob(new TreeNode(5)));
    }

    @Test
    void testOnlyLeftSubtree() {
        // 根3，左孩子4，4的左孩子1
        TreeNode node1 = new TreeNode(1);
        TreeNode node4 = new TreeNode(4, node1, null);
        TreeNode root = new TreeNode(3, node4, null);
        assertEquals(4, solution.rob(root));
    }

    @Test
    void testTwoLevelFullTree() {
        // [3,4,5]
        TreeNode root = new TreeNode(3, new TreeNode(4), new TreeNode(5));
        assertEquals(9, solution.rob(root));
    }

    @Test
    void testThreeLevelTree() {
        // [3,2,3,null,3,null,1]
        TreeNode leftRight = new TreeNode(3);
        TreeNode left = new TreeNode(2, null, leftRight);

        TreeNode rightRight = new TreeNode(1);
        TreeNode right = new TreeNode(3, null, rightRight);

        TreeNode root = new TreeNode(3, left, right);
        assertEquals(7, solution.rob(root));
    }
}