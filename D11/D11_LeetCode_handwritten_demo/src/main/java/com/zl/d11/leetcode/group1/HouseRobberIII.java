package com.zl.d11.leetcode.group1;

import com.zl.d11.leetcode.model.TreeNode;

/**
 * LeetCode 337. 打家劫舍 III
 */
public class HouseRobberIII {

    public int rob(TreeNode root) {
        int[] res = dfs(root);
        return Math.max(res[0], res[1]);
    }

    /**
     * 返回数组：[0]不偷当前节点的最大金额，[1]偷当前节点的最大金额
     */
    private int[] dfs(TreeNode node) {
        if (node == null) {
            return new int[]{0, 0};
        }

        int[] left = dfs(node.left);
        int[] right = dfs(node.right);

        // 偷当前：左右子树都不能偷
        int steal = node.val + left[0] + right[0];
        // 不偷当前：左右子树各自选最优
        int notSteal = Math.max(left[0], left[1]) + Math.max(right[0], right[1]);

        return new int[]{notSteal, steal};
    }
}