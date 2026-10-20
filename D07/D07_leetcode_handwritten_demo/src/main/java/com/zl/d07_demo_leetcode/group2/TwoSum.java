package com.zl.d07_demo_leetcode.group2;

import java.util.HashMap;
import java.util.Map;

public class TwoSum {

    /**
     * 哈希表法：一次遍历完成
     * @param nums 输入数组
     * @param target 目标和
     * @return 两个元素的下标数组
     */
    public int[] twoSum(int[] nums, int target) {
        // key: 数值, value: 对应下标
        Map<Integer, Integer> map = new HashMap<>();
        
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            // 检查补数是否已在哈希表中
            if (map.containsKey(complement)) {
                return new int[]{map.get(complement), i};
            }
            // 不存在则存入当前值
            map.put(nums[i], i);
        }
        // 题目保证有解，此处仅作异常兜底
        throw new IllegalArgumentException("No two sum solution");
    }
}
