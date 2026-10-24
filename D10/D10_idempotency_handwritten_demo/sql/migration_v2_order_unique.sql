-- =====================================================================
-- 迁移 v2：order_info 增加 tenant_id 并建立 (tenant_id, idempotency_key)
--         联合唯一约束，作为订单最终防重防线。
-- 适用：已执行过 v1 schema.sql 的本地演示库 ai_db。
--
-- 「重要说明」
-- 1. 本库 order_info / idempotency_record 仅存放接口验证产生的演示数据，
--    且旧 order_info 缺少 tenant_id，无法可靠回填多租户历史行，
--    因此本迁移采用「重建表」方式（DROP + CREATE），会清空两表演示数据。
-- 2. 生产环境禁止 DROP，应使用在线 DDL：
--    ALTER TABLE order_info
--      ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'default' AFTER id,
--      ADD UNIQUE KEY uk_tenant_idempotency (tenant_id, idempotency_key),
--      DROP KEY idx_idempotency_key;
--    并在加唯一约束前先完成数据回填与去重。
-- =====================================================================

SET NAMES utf8mb4;

-- 订单表引用幂等键，先删订单表，再删幂等记录表
DROP TABLE IF EXISTS `order_info`;
DROP TABLE IF EXISTS `idempotency_record`;

-- ---------------------------------------------------------------------
-- 幂等记录表（与 schema.sql 保持一致）
-- ---------------------------------------------------------------------
CREATE TABLE `idempotency_record` (
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
-- 订单表：新增 tenant_id 与联合唯一约束
-- ---------------------------------------------------------------------
CREATE TABLE `order_info` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `tenant_id`       VARCHAR(32)  NOT NULL DEFAULT 'default' COMMENT '租户标识，作用域隔离',
    `product_id`      BIGINT       NOT NULL COMMENT '商品ID',
    `quantity`        INT          NOT NULL COMMENT '数量',
    `status`          VARCHAR(16)  NOT NULL COMMENT '状态：CREATED/PAID/CANCELLED',
    `idempotency_key` VARCHAR(64)  NOT NULL COMMENT '关联的幂等键',
    `created_at`      DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_idempotency` (`tenant_id`, `idempotency_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';
