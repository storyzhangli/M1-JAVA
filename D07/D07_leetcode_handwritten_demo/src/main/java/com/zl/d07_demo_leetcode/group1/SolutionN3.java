package com.zl.d07_demo_leetcode.group1;

public class SolutionN3 {
    public void moveZeroes(int[] nums) {
        int nonZeroIndex = 0;
        // 第一步：把所有非零元素按顺序移到前面
        for (int num : nums) {
            if (num != 0) {
                nums[nonZeroIndex++] = num;
            }
        }
        // 第二步：末尾剩余位置全部置0
        for (int i = nonZeroIndex; i < nums.length; i++) {
            nums[i] = 0;
        }
    }

    // 本地测试
    public static void main(String[] args) {
        SolutionN3 solutionN3 = new SolutionN3();
        int[] nums = {0, 1, 0, 3, 12};
        solutionN3.moveZeroes(nums);
        for (int num : nums) {
            System.out.print(num + " "); // 输出 1 3 12 0 0
        }
    }
}
