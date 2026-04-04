CREATE DATABASE IF NOT EXISTS ticket_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ticket_user;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) COMMENT '用户昵称',
    email VARCHAR(100),
    phone VARCHAR(20),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1-正常 2-禁用 3-已删除',
    is_admin TINYINT(1) DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- admin / 123456
INSERT INTO users (id, username, password, nickname, email, phone, is_admin)
VALUES (1, 'admin', '$2a$10$hD3PRCRThYVjocahxM06yu608Xl91AWulxWO9KL7GoXBK04iH.I8e', '管理员', 'admin@example.com', '13800138001', 1);


-- user / 123456
INSERT INTO users (id, username, password, nickname, email, phone, is_admin)
VALUES (2, 'user', '$2a$10$hD3PRCRThYVjocahxM06yu608Xl91AWulxWO9KL7GoXBK04iH.I8e', '测试用户', 'test@example.com', '13800138000', 0);
