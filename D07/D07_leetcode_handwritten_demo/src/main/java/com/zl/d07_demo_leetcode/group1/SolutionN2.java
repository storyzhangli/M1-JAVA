package com.zl.d07_demo_leetcode.group1;

public class SolutionN2 {
    public int maxSubArray(int[] nums) {
        int currentMax = nums[0];
        int maxSum = nums[0];
        for (int i = 1; i < nums.length; i++) {
            currentMax = Math.max(nums[i], currentMax + nums[i]);
            maxSum = Math.max(maxSum, currentMax);
        }
        return maxSum;
    }

    // 本地测试
    public static void main(String[] args) {
        SolutionN2 solutionN2 = new SolutionN2();
        int[] nums = {-2, 1, -3, 4, -1, 2, 1, -5, 4};
        System.out.println(solutionN2.maxSubArray(nums)); // 输出 6（对应子数组 [4,-1,2,1]）
    }
}
