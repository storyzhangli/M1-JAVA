package com.zl.d10.idempotent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zl.d10.idempotent.common.ApiResponse;
import com.zl.d10.idempotent.common.OrderResult;
import com.zl.d10.idempotent.dto.OrderCreateRequest;
import com.zl.d10.idempotent.dto.OrderResponse;
import com.zl.d10.idempotent.entity.IdempotencyRecord;
import com.zl.d10.idempotent.exception.IdempotencyException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 订单服务门面（非事务）：协调幂等记录的写入、冲突处理与业务执行。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 接口编码。 */
    public static final String API_CODE = "ORDER_CREATE";

    /** 幂等记录保留时长（小时）。 */
    private static final int RETENTION_HOURS = 24;

    private final IdempotencyService idempotencyService;
    private final OrderTransactionService transactionService;
    private final ObjectMapper objectMapper;

    /**
     * 创建订单（幂等）。
     */
    public OrderResult createOrder(String tenantId, String key, OrderCreateRequest request) {
        String digest = sha256(request);
        LocalDateTime now = LocalDateTime.now();

        // 1. 先尝试插入 PROCESSING 记录（抢占幂等键）
        IdempotencyRecord record = new IdempotencyRecord();
        record.setTenantId(tenantId);
        record.setApiCode(API_CODE);
        record.setIdempotencyKey(key);
        record.setRequestDigest(digest);
        record.setStatus(IdempotencyService.PROCESSING);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setExpireAt(now.plusHours(RETENTION_HOURS));

        try {
            idempotencyService.insertProcessing(record);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突：该幂等键已被占用，转入冲突处理
            return handleDuplicate(tenantId, key, digest, request);
        }

        // 2. 抢占成功：执行业务（独立事务）
        try {
            OrderResponse response = transactionService.createAndSucceed(
                    tenantId, API_CODE, key, request);
            return new OrderResult(201, ApiResponse.ok("创建成功", response));
        } catch (Exception e) {
            // 业务失败：标记 FAILED 以便后续重试
            idempotencyService.markFailed(tenantId, API_CODE, key);
            throw e;
        }
    }

    /**
     * 幂等键冲突处理：比对摘要并依据既有记录状态决定返回。
     */
    private OrderResult handleDuplicate(String tenantId, String key, String digest,
                                        OrderCreateRequest request) {
        IdempotencyRecord rec = idempotencyService.find(tenantId, API_CODE, key);
        if (rec == null) {
            throw new IdempotencyException(409, 40903, "幂等记录状态异常");
        }

        // 同键不同参数：拒绝
        if (!digest.equals(rec.getRequestDigest())) {
            throw new IdempotencyException(409, 40901, "幂等键冲突，请求参数不一致");
        }

        switch (rec.getStatus()) {
            case IdempotencyService.SUCCEEDED:
                // 已成功：返回原结果
                OrderResponse response = readResult(rec.getResultBody());
                return new OrderResult(200, ApiResponse.ok("请求已处理", response));
            case IdempotencyService.PROCESSING:
                // 处理中：提示勿重复提交
                throw new IdempotencyException(409, 40902, "请求正在处理中，请勿重复提交");
            case IdempotencyService.FAILED:
                // 之前失败：允许重新执行
                OrderResponse retried = transactionService.createAndSucceed(
                        tenantId, API_CODE, key, request);
                return new OrderResult(201, ApiResponse.ok("创建成功", retried));
            default:
                throw new IdempotencyException(409, 40903, "幂等记录状态异常");
        }
    }

    private OrderResponse readResult(String resultBody) {
        try {
            return objectMapper.readValue(resultBody, OrderResponse.class);
        } catch (Exception e) {
            throw new IdempotencyException(409, 40903, "幂等结果解析失败");
        }
    }

    /**
     * 计算请求体的 SHA-256 摘要。
     * 以 DTO 的规范化 JSON 为输入，保证相同逻辑参数得到相同摘要。
     */
    private String sha256(OrderCreateRequest request) {
        try {
            String canonical = objectMapper.writeValueAsString(request);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算请求摘要失败", e);
        }
    }
}
