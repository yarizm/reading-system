package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_comment")
public class SysComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long bookId;
    private String content;
    private Integer rating; // 1-5分

    // === 新增字段 ===
    private Long parentId;      // 父评论ID
    private Long replyUserId;   // 被回复人ID
    private Integer likeCount;  // 点赞数

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // === 用于前端展示的字段 (非数据库列) ===
    @TableField(exist = false)
    private String nickname;    // 评论人昵称
    @TableField(exist = false)
    private String avatar;      // 评论人头像

    @TableField(exist = false)
    private String replyNickname; // 被回复人的昵称 (用于显示 "回复 @某某")

    @TableField(exist = false)
    private Boolean isLiked;    // 当前用户是否已赞

    @TableField(exist = false)
    private List<SysComment> children; // 子评论列表 (楼中楼)
}