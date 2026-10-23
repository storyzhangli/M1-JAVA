-- =====================================================================
-- D10_idempotency_handwritten_demo 建表脚本
-- 数据库：ai_db
-- 引擎：InnoDB（支持事务、行级锁、唯一约束）
-- 字符集：utf8mb4
-- 幂等方案：数据库唯一约束 + 事务性幂等记录
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 幂等记录表 idempotency_record
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `idempotency_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       VARCHAR(32)  NOT NULL DEFAULT 'default' COMMENT '租户标识，作用域隔离',
    `api_code`        VARCHAR(32)  NOT NULL COMMENT '接口编码，如 ORDER_CREATE',
    `idempotency_key` VARCHAR(64)  NOT NULL COMMENT '幂等键',
    `request_digest`  VARCHAR(64)  NOT NULL COMMENT '请求体 SHA-256 摘要',
    `status`          VARCHAR(16)  NOT NULL COMMENT '状态：PROCESSING/SUCCEEDED/FAILED',
    `result_body`     TEXT         NULL COMMENT '成功响应体 JSON，用于重复请求返回',
    `created_at`      DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL COMMENT '更新时间',
    `expire_at`       DATETIME     NOT NULL COMMENT '过期时间，用于清理',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_api_idempotency` (`tenant_id`, `api_code`, `idempotency_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '幂等记录表';

-- ---------------------------------------------------------------------
-- 2. 订单表 order_info
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_info` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `product_id`      BIGINT       NOT NULL COMMENT '商品ID',
    `quantity`        INT          NOT NULL COMMENT '数量',
    `status`          VARCHAR(16)  NOT NULL COMMENT '状态：CREATED/PAID/CANCELLED',
    `idempotency_key` VARCHAR(64)  NOT NULL COMMENT '关联的幂等键',
    `created_at`      DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_idempotency_key` (`idempotency_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';
