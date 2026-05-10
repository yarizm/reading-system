-- 书籍审核请求表（支持新书公开、编辑审核、下架审核）
CREATE TABLE book_review_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL COMMENT '关联 sys_book.id',
    user_id BIGINT NOT NULL COMMENT '发起审核的用户ID',
    request_type VARCHAR(20) NOT NULL COMMENT 'new=新书公开, edit=编辑审核, delist=下架审核',
    new_book_data TEXT COMMENT 'JSON快照(edit类型时存储变更后的书籍全量字段)',
    status INT DEFAULT 0 COMMENT '0=待审核, 1=通过, 2=驳回',
    reject_reason TEXT COMMENT '驳回原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    review_time DATETIME COMMENT '审核时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
