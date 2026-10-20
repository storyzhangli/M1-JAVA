package com.zl.d07_demo_leetcode.group1;

import java.util.HashMap;
import java.util.Map;

public class SolutionN1 {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> numIndexMap = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (numIndexMap.containsKey(complement)) {
                return new int[]{numIndexMap.get(complement), i};
            }
            numIndexMap.put(nums[i], i);
        }
        throw new IllegalArgumentException("No two sum solution");
    }

    // 本地测试
    public static void main(String[] args) {
        SolutionN1 solutionN1 = new SolutionN1();
        int[] nums = {2, 7, 11, 15};
        int target = 9;
        int[] result = solutionN1.twoSum(nums, target);
        System.out.println("[" + result[0] + ", " + result[1] + "]"); // 输出 [0, 1]
    }
}
