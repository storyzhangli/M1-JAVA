package com.zl.d10.idempotent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zl.d10.idempotent.dto.OrderCreateRequest;
import com.zl.d10.idempotent.dto.OrderResponse;
import com.zl.d10.idempotent.entity.IdempotencyRecord;
import com.zl.d10.idempotent.entity.OrderInfo;
import com.zl.d10.idempotent.mapper.IdempotencyRecordMapper;
import com.zl.d10.idempotent.mapper.OrderInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单事务服务：业务数据与幂等记录在同一事务中提交/回滚。
 */
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderInfoMapper orderMapper;
    private final IdempotencyRecordMapper recordMapper;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * 创建订单并将幂等记录置为 SUCCEEDED。
     * 订单写入与幂等记录更新处于同一事务，任一失败整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createAndSucceed(String tenantId, String apiCode, String key,
                                          OrderCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 写入订单
        OrderInfo order = new OrderInfo();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("CREATED");
        order.setIdempotencyKey(key);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        // 2. 构造响应
        OrderResponse response = new OrderResponse(
                order.getId(), order.getProductId(), order.getQuantity(), order.getStatus());

        // 3. 幂等记录置为 SUCCEEDED 并保存结果
        IdempotencyRecord rec = idempotencyService.find(tenantId, apiCode, key);
        if (rec != null) {
            rec.setStatus(IdempotencyService.SUCCEEDED);
            rec.setResultBody(toJson(response));
            rec.setUpdatedAt(now);
            recordMapper.updateById(rec);
        }
        return response;
    }

    private String toJson(OrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化响应失败", e);
        }
    }
}
