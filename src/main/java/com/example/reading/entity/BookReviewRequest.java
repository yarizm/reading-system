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
 * 书籍审核请求表
 * request_type: new=新书公开, edit=编辑审核, delist=下架审核
 * status: 0=待审核, 1=通过, 2=驳回
 */
@Getter
@Setter
@TableName("book_review_request")
public class BookReviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("book_id")
    private Long bookId;

    @TableField("user_id")
    private Long userId;

    @TableField("request_type")
    private String requestType;

    @TableField("new_book_data")
    private String newBookData;

    @TableField("status")
    private Integer status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("review_time")
    private LocalDateTime reviewTime;

    // 虚拟字段：关联查询用
    @TableField(exist = false)
    private String bookTitle;

    @TableField(exist = false)
    private String bookAuthor;

    @TableField(exist = false)
    private String bookCoverUrl;

    @TableField(exist = false)
    private String bookDescription;

    @TableField(exist = false)
    private String bookCategory;

    @TableField(exist = false)
    private String bookFilePath;

    @TableField(exist = false)
    private String uploaderNickname;
}
