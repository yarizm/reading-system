-- 用户表增加手机/邮箱
ALTER TABLE sys_user ADD COLUMN email VARCHAR(100) COMMENT '邮箱';
ALTER TABLE sys_user ADD COLUMN phone VARCHAR(20) COMMENT '手机号';

-- 验证码记录表
CREATE TABLE sys_validation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target VARCHAR(100) NOT NULL COMMENT '手机号或邮箱',
    code VARCHAR(10) NOT NULL COMMENT '验证码',
    type INT NOT NULL COMMENT '1:注册 2:登录 3:找回密码',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
