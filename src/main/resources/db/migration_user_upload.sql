-- 用户上传书籍 + 管理员审核功能 - 数据库变更
-- 在 sys_book 表新增两个字段

ALTER TABLE sys_book ADD COLUMN uploader_id BIGINT DEFAULT NULL COMMENT '上传用户ID，NULL表示管理员上传';
ALTER TABLE sys_book ADD COLUMN status INT DEFAULT 2 COMMENT '状态：0=私有, 1=待审核, 2=已公开, 3=已驳回';

-- status 默认值为 2（已公开），现有管理员上传的书籍不受影响

-- 管理员封禁功能
ALTER TABLE sys_user ADD COLUMN is_banned INT DEFAULT 0 COMMENT '是否封禁：0正常，1封禁';
