CREATE DATABASE IF NOT EXISTS ticket_stock DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ticket_stock;

CREATE TABLE `stock` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `concert_id` BIGINT UNSIGNED NOT NULL COMMENT '演唱会ID',
  `grade_id` BIGINT UNSIGNED NOT NULL COMMENT '档位ID',
  `available_stock` INT NOT NULL COMMENT '可用库存',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_concert_grade` (`concert_id`, `grade_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表（不选座模式）';

CREATE TABLE IF NOT EXISTS stock_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    concert_id BIGINT NOT NULL COMMENT '演唱会ID',
    grade_id BIGINT NOT NULL COMMENT '档位ID',
    order_no VARCHAR(64),
    change_amount INT NOT NULL,
    before_stock INT NOT NULL,
    after_stock INT NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_concert_id (concert_id),
    INDEX idx_grade_id (grade_id),
    INDEX idx_order_no (order_no),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO stock (concert_id, grade_id, available_stock) 
VALUES 
(1, 1, 100),
(1, 2, 500),
(1, 3, 1000),
(2, 4, 100),
(2, 5, 500),
(2, 6, 1000),
(3, 7, 100),
(3, 8, 500),
(3, 9, 1000);
