package com.zl.d11.leetcode.group1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LongestIncreasingSubsequenceTest {

    private final LongestIncreasingSubsequence solution = new LongestIncreasingSubsequence();

    @Test
    void testEmptyArray() {
        assertEquals(0, solution.lengthOfLIS(new int[]{}));
    }

    @Test
    void testSingleElement() {
        assertEquals(1, solution.lengthOfLIS(new int[]{5}));
    }

    @Test
    void testStrictlyDecreasing() {
        assertEquals(1, solution.lengthOfLIS(new int[]{5, 4, 3, 2, 1}));
    }

    @Test
    void testStrictlyIncreasing() {
        assertEquals(5, solution.lengthOfLIS(new int[]{1, 2, 3, 4, 5}));
    }

    @Test
    void testDuplicateElements() {
        assertEquals(1, solution.lengthOfLIS(new int[]{2, 2, 2}));
    }

    @Test
    void testLongestInMiddle() {
        // [10,9,2,5,3,7,101,18] → 最长：2,5,7,101 → 长度4
        int[] nums = {10, 9, 2, 5, 3, 7, 101, 18};
        assertEquals(4, solution.lengthOfLIS(nums));
    }
}