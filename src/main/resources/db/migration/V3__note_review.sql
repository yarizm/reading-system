-- V3__note_review.sql
-- 知识回顾系统：SM-2 间隔重复

CREATE TABLE IF NOT EXISTS `note_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `note_id` BIGINT NOT NULL,
  `ease_factor` FLOAT DEFAULT 2.5,
  `interval_days` INT DEFAULT 1,
  `repetitions` INT DEFAULT 0,
  `next_review_date` DATE NOT NULL,
  `last_review_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_note` (`user_id`, `note_id`),
  KEY `idx_user_next_review` (`user_id`, `next_review_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
