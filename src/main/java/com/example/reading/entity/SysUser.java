package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author CodingAssistant
 * @since 2026-01-31
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 密码(加密存储)
     */
    @TableField("password")
    private String password;

    /**
     * 昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 角色 0:普通用户 1:管理员
     */
    @TableField("role")
    private Integer role;

    /**
     * 年龄(用于初步推荐策略)
     */
    @TableField("age")
    private Integer age;

    /**
     * 阅读偏好标签(JSON或逗号分隔，如: 科幻,历史)，用于AI推荐
     */
    @TableField("preferences")
    private String preferences;

    /**
     * 健康阅读时长限制(分钟)，默认60分钟
     */
    @TableField("health_limit_time")
    private Integer healthLimitTime;

    /**
     * AI朗读偏好音色ID
     */
    @TableField("preferred_voice")
    private String preferredVoice;

    /**
     * 注册时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("avatar")
    private String avatar;   // === 新增 ===

    @TableField("shelf_visible")
    private Integer shelfVisible; // 0:私密 1:公开，默认1

    @TableField("info_visible")
    private Integer infoVisible; // 0:私密 1:公开，默认1

    @TableField("email")
    private String email;

    @TableField("phone")
    private String phone;
}
