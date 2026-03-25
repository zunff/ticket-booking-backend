-- ============================================
-- 增量脚本: 订单表增加失败原因字段
-- 版本: V1.0.1
-- 日期: 2026-03-25
-- 描述:
--   1. 增加 fail_reason 字段记录订单失败原因
--   2. 修改 status 字段注释，增加状态 0（处理中）
-- ============================================

USE ticket_order;

-- 1. 增加 fail_reason 字段
ALTER TABLE orders
ADD COLUMN fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因（当status=4时记录）'
AFTER status;

-- 2. 修改 status 字段注释（MySQL 8.0+）
ALTER TABLE orders
MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0-处理中 1-待支付 2-已支付 3-已取消 4-失败';