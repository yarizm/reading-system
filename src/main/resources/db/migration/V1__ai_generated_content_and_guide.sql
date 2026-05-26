-- AI 生成内容持久化
CREATE TABLE IF NOT EXISTS ai_generated_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL
        COMMENT 'note_summary | mindmap | weekly_report | monthly_report | quiz | book_review_draft',
    reference_type VARCHAR(30)
        COMMENT 'book | note | user',
    reference_id BIGINT
        COMMENT '关联实体 ID',
    title VARCHAR(200),
    content TEXT NOT NULL,
    metadata JSON
        COMMENT '额外结构化数据',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_id, content_type),
    INDEX idx_ref (reference_type, reference_id)
) COMMENT 'AI生成内容缓存';

-- 向导对话历史
CREATE TABLE IF NOT EXISTS guide_conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    dify_conversation_id VARCHAR(100),
    title VARCHAR(200),
    last_message_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
) COMMENT '向导对话索引';
