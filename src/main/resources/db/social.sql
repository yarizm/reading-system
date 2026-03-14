-- ========================================
-- 社交功能建表 SQL
-- ========================================

-- 1. 好友关系表
CREATE TABLE user_friendship (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '发起者ID',
    friend_id BIGINT NOT NULL COMMENT '接收者ID',
    status INT NOT NULL DEFAULT 0 COMMENT '0:待确认 1:已接受 2:已拒绝',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (user_id, friend_id)
) COMMENT='好友关系表';

-- 2. 聊天消息表
CREATE TABLE chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT NOT NULL COMMENT '接收者ID',
    content TEXT NOT NULL COMMENT '消息内容',
    is_read INT DEFAULT 0 COMMENT '0:未读 1:已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender_receiver (sender_id, receiver_id),
    INDEX idx_receiver_read (receiver_id, is_read)
) COMMENT='聊天消息表';

-- 3. 书籍分享表
CREATE TABLE book_share (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id BIGINT NOT NULL COMMENT '分享者ID',
    receiver_id BIGINT NOT NULL COMMENT '接收者ID',
    book_id BIGINT NOT NULL COMMENT '书籍ID',
    message VARCHAR(200) COMMENT '分享留言',
    is_read INT DEFAULT 0 COMMENT '0:未读 1:已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_receiver (receiver_id)
) COMMENT='书籍分享表';
