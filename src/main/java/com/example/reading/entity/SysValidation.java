package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_validation")
public class SysValidation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String target; // 手机号或邮箱
    private String code;
    private Integer type; // 1:注册 2:登录 3:找回密码
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
