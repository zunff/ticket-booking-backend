-- ============================================
-- 增量脚本: 演唱会表增加限购字段
-- 版本: V1.0.2
-- 日期: 2026-03-25
-- 描述: 演唱会级别限购，同一演唱会每人限购 N 张
-- ============================================

USE ticket_ticket;

-- 增加 purchase_limit 字段
ALTER TABLE concerts
ADD COLUMN purchase_limit INT NOT NULL DEFAULT 1 COMMENT '每人限购数量'
AFTER end_sale_time;

-- 更新已有数据（设置默认限购数量）
UPDATE concerts SET purchase_limit = 4 WHERE purchase_limit = 1;
