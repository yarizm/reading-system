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
 * 书籍评论表
 * </p>
 *
 * @author CodingAssistant
 * @since 2026-01-31
 */
@Getter
@Setter
@TableName("book_comment")
public class BookComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 书籍ID
     */
    @TableField("book_id")
    private Long bookId;

    /**
     * 评论内容
     */
    @TableField("content")
    private String content;

    /**
     * 评论时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
