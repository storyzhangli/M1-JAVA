package com.zl.d09.order.fallback;

import com.zl.d09.order.client.InventoryClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存服务降级工厂
 * 可获取异常信息，根据不同异常返回对应降级结果
 */
@Component
public class InventoryFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        return new InventoryClient() {
            @Override
            public InventoryVO getInventory(Long skuId) {
                // 记录降级原因，生产环境用日志
                System.err.println("库存服务降级，skuId=" + skuId + "，异常：" + cause.getMessage());
                // 降级返回：明确标记降级，库存返回0
                return new InventoryVO(skuId, 0, "降级-库存不可用");
            }

            @Override
            public String delayTest(Integer seconds) {
                return "降级：请求超时";
            }

            @Override
            public String errorTest() {
                return "降级：服务异常";
            }
        };
    }
}