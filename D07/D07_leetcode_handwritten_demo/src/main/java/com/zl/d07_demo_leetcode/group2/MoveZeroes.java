package com.zl.d07_demo_leetcode.group2;

public class MoveZeroes {

    public void moveZeroes(int[] nums) {
        if (nums == null || nums.length <= 1) {
            return;
        }
        
        int left = 0;
        // 快指针遍历数组
        for (int right = 0; right < nums.length; right++) {
            if (nums[right] != 0) {
                // 非零元素移到慢指针位置
                nums[left] = nums[right];
                left++;
            }
        }
        // 慢指针之后全部置0
        for (int i = left; i < nums.length; i++) {
            nums[i] = 0;
        }
    }
}
