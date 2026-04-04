CREATE DATABASE IF NOT EXISTS ticket_concert DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ticket_concert;

CREATE TABLE `concerts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(128) NOT NULL COMMENT '演唱会名称',
  `venue` VARCHAR(128) NOT NULL COMMENT '场馆',
  `show_time` DATETIME NOT NULL COMMENT '演出时间',
  `start_sale_time` DATETIME NOT NULL COMMENT '开始售票时间',
  `end_sale_time` DATETIME NOT NULL COMMENT '结束售票时间',
  `purchase_limit` INT NOT NULL DEFAULT 1 COMMENT '每人限购数量',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-已关闭，1-开售中（根据start_sale_time和end_sale_time动态计算）',
  `preheat_job_id` INT DEFAULT NULL COMMENT '预热任务ID（XXL-Job）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_show_time` (`show_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演唱会场次表';

CREATE TABLE `ticket_grade` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `concert_id` BIGINT UNSIGNED NOT NULL COMMENT '演唱会ID',
  `grade_name` VARCHAR(64) NOT NULL COMMENT '档位名称（如：VIP内场）',
  `price` INT NOT NULL COMMENT '票价（单位：分）',
  `total_stock` INT NOT NULL COMMENT '总库存',
  `is_selected_seat` TINYINT NOT NULL DEFAULT 0 COMMENT '是否选座：0-不选座（先到先得），1-选座',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_concert_id` (`concert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票价档位表';

INSERT INTO concerts (name, venue, show_time, start_sale_time, end_sale_time, status)
VALUES ('2024跨年演唱会', '国家体育场', '2024-12-31 20:00:00', NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1);

INSERT INTO ticket_grade (concert_id, grade_name, price, total_stock, is_selected_seat)
VALUES
(1, 'VIP内场', 29900, 100, 0),
(1, '普通看台', 19900, 500, 0),
(1, '山顶票', 9900, 1000, 0);