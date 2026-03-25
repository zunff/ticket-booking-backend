CREATE DATABASE IF NOT EXISTS ticket_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ticket_order;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    concert_id BIGINT NOT NULL COMMENT '演唱会ID',
    grade_id BIGINT NOT NULL COMMENT '档位ID',
    quantity INT NOT NULL DEFAULT 1,
    total_price INT NOT NULL COMMENT '总价（单位：分）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-处理中 1-待支付 2-已支付 3-已取消 4-失败',
    fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因（当status=4时记录）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_concert_id (concert_id),
    INDEX idx_grade_id (grade_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
