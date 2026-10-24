package com.zl.d11.leetcode.group1;

import com.zl.d11.leetcode.model.TreeNode;

/**
 * LeetCode 124. 二叉树中的最大路径和
 */
public class MaxPathSum {
    private int maxSum;

    public int maxPathSum(TreeNode root) {
        maxSum = Integer.MIN_VALUE;
        dfs(root);
        return maxSum;
    }

    /**
     * 返回以当前节点为起点的单边最大路径和（只能选左或右分支，用于向上拼接）
     */
    private int dfs(TreeNode node) {
        if (node == null) {
            return 0;
        }

        // 子树贡献为负则舍弃，取0
        int leftGain = Math.max(dfs(node.left), 0);
        int rightGain = Math.max(dfs(node.right), 0);

        // 当前节点作为最高点的路径和，更新全局最大值
        maxSum = Math.max(maxSum, node.val + leftGain + rightGain);

        // 返回单边最大和
        return node.val + Math.max(leftGain, rightGain);
    }
}