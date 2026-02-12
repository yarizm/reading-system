-- 书单表
CREATE TABLE IF NOT EXISTS booklist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  share_code VARCHAR(32) UNIQUE COMMENT '分享码，用于生成链接',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 书单-书籍关联表
CREATE TABLE IF NOT EXISTS booklist_book (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booklist_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  UNIQUE KEY uk_list_book (booklist_id, book_id)
);

-- sys_user 新增字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS shelf_visible TINYINT DEFAULT 1 COMMENT '0:私密 1:公开';
