package com.zl.d07_demo_leetcode.group2;

public class BestTimeToBuyAndSellStock {

    public int maxProfit(int[] prices) {
        if (prices == null || prices.length < 2) {
            return 0;
        }
        
        int minPrice = prices[0];
        int maxProfit = 0;
        
        for (int i = 1; i < prices.length; i++) {
            // 更新最大利润
            maxProfit = Math.max(maxProfit, prices[i] - minPrice);
            // 更新历史最低价格
            minPrice = Math.min(minPrice, prices[i]);
        }
        return maxProfit;
    }
}
