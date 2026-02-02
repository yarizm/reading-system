package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_paragraph_comment")
public class SysParagraphComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Long userId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private String content;
    private String quote; // 记录当时的段落内容，防止书籍修改后错位

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // === 以下字段用于前端展示 (关联查询 user 表) ===
    @TableField(exist = false)
    private String nickname;
    @TableField(exist = false)
    private String avatar;
    private Integer likeCount; // 对应数据库字段

    @TableField(exist = false)
    private Boolean isLiked;   // 当前用户是否点赞过 (前端展示用)
}