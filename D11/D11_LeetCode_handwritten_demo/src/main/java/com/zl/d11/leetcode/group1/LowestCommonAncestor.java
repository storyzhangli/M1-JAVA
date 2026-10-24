package com.zl.d11.leetcode.group1;

import com.zl.d11.leetcode.model.TreeNode;

/**
 * LeetCode 236. 二叉树的最近公共祖先
 */
public class LowestCommonAncestor {

    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        // 终止条件：空节点，或当前节点就是p/q
        if (root == null || root == p || root == q) {
            return root;
        }

        TreeNode left = lowestCommonAncestor(root.left, p, q);
        TreeNode right = lowestCommonAncestor(root.right, p, q);

        // 左右都不为空，当前节点就是公共祖先
        if (left != null && right != null) {
            return root;
        }
        // 哪边有结果返回哪边
        return left != null ? left : right;
    }
}