package com.zl.d11.leetcode.group1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoinChangeTest {

    private final CoinChange solution = new CoinChange();

    @Test
    void testAmountZero() {
        assertEquals(0, solution.coinChange(new int[]{1, 2, 5}, 0));
    }

    @Test
    void testAllCoinsLargerThanAmount() {
        assertEquals(-1, solution.coinChange(new int[]{5, 10}, 3));
    }

    @Test
    void testNoSolution() {
        assertEquals(-1, solution.coinChange(new int[]{2}, 3));
    }

    @Test
    void testStandardCase() {
        // 11 = 5+5+1 → 3个
        assertEquals(3, solution.coinChange(new int[]{1, 2, 5}, 11));
    }

    @Test
    void testSingleCoin() {
        assertEquals(5, solution.coinChange(new int[]{1}, 5));
    }

    @Test
    void testMinCoinCount() {
        // 最优解用大面额
        assertEquals(2, solution.coinChange(new int[]{1, 3, 5}, 6));
    }
}