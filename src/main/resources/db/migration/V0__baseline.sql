-- ============================================
-- Baseline: 完整数据库初始化
-- 所有 V1-V7 增量迁移已合并到此文件
-- ============================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(255) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `nickname` VARCHAR(255) DEFAULT NULL,
    `role` INT DEFAULT 0 COMMENT '0=普通用户, 1=管理员',
    `age` INT DEFAULT NULL,
    `preferences` TEXT DEFAULT NULL,
    `health_limit_time` INT DEFAULT 60 COMMENT '健康限制时间(分钟)',
    `preferred_voice` VARCHAR(100) DEFAULT 'zh-CN-XiaoxiaoNeural',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `avatar` VARCHAR(500) DEFAULT NULL,
    `shelf_visible` INT DEFAULT 0 COMMENT '书架可见性',
    `info_visible` INT DEFAULT 0 COMMENT '个人信息可见性',
    `email` VARCHAR(255) DEFAULT NULL COMMENT '邮箱(验证码登录)',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号(验证码登录)',
    `is_banned` INT DEFAULT 0 COMMENT '是否被封禁',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2-15. 业务表：书籍/章节/评论/笔记/书架/社交/聊天/书单/审核
CREATE TABLE IF NOT EXISTS `sys_book` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(500) NOT NULL,
    `author` VARCHAR(255) DEFAULT NULL,
    `description` TEXT DEFAULT NULL,
    `cover_url` VARCHAR(500) DEFAULT NULL,
    `file_path` VARCHAR(500) DEFAULT NULL,
    `category` VARCHAR(100) DEFAULT NULL,
    `tags` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `uploader_id` BIGINT DEFAULT NULL COMMENT '上传者ID, NULL=管理员上传',
    `status` INT DEFAULT 0 COMMENT '0=私有, 1=待审核, 2=已公开, 3=已驳回, 4=已下架',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_uploader` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_chapter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `book_id` BIGINT NOT NULL,
    `title` VARCHAR(500) DEFAULT NULL,
    `content` LONGTEXT DEFAULT NULL,
    `word_count` INT DEFAULT 0,
    `sort` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT NULL,
    `audio_url` VARCHAR(500) DEFAULT NULL,
    `kb_document_id` VARCHAR(64) DEFAULT NULL COMMENT 'Dify KB document ID',
    PRIMARY KEY (`id`),
    KEY `idx_book_id` (`book_id`),
    KEY `idx_sort` (`book_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `book_id` BIGINT NOT NULL,
    `content` TEXT DEFAULT NULL,
    `rating` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `parent_id` BIGINT DEFAULT 0 COMMENT '父评论ID, 0=顶级评论',
    `reply_user_id` BIGINT DEFAULT NULL COMMENT '回复目标用户ID',
    `like_count` INT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_book_id` (`book_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_comment_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `comment_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_paragraph_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `book_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `chapter_index` INT NOT NULL,
    `paragraph_index` INT NOT NULL,
    `content` TEXT DEFAULT NULL,
    `quote` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `like_count` INT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_book_chapter` (`book_id`, `chapter_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_paragraph_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `comment_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_note` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `book_id` BIGINT NOT NULL,
    `selected_text` TEXT DEFAULT NULL,
    `content` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_book` (`user_id`, `book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_bookshelf` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `book_id` BIGINT NOT NULL,
    `last_read_time` DATETIME DEFAULT NULL,
    `progress_index` INT DEFAULT 0,
    `is_finished` INT DEFAULT 0,
    `current_chapter_index` INT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_book` (`user_id`, `book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_friendship` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `friend_id` BIGINT NOT NULL,
    `status` INT DEFAULT 0 COMMENT '0=待确认, 1=已通过, 2=已拒绝',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pair` (`user_id`, `friend_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_friend_status` (`friend_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sender_id` BIGINT NOT NULL,
    `receiver_id` BIGINT NOT NULL,
    `content` TEXT DEFAULT NULL,
    `is_read` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation` (`sender_id`, `receiver_id`),
    KEY `idx_receiver_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `book_share` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sender_id` BIGINT NOT NULL,
    `receiver_id` BIGINT NOT NULL,
    `book_id` BIGINT NOT NULL,
    `message` TEXT DEFAULT NULL,
    `is_read` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_receiver` (`receiver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `booklist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `share_code` VARCHAR(20) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_code` (`share_code`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `booklist_book` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `booklist_id` BIGINT NOT NULL,
    `book_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_list_book` (`booklist_id`, `book_id`),
    KEY `idx_booklist` (`booklist_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `book_review_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `book_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `request_type` VARCHAR(20) NOT NULL COMMENT 'new, edit, delist',
    `new_book_data` TEXT DEFAULT NULL COMMENT 'JSON格式的修改后数据',
    `status` INT DEFAULT 0 COMMENT '0=待处理, 1=已通过, 2=已驳回',
    `reject_reason` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `review_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_book_id` (`book_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
