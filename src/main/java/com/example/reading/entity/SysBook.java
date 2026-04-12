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
 * 书籍信息表
 * status: 0=私有(用户上传未公开), 1=待审核, 2=已公开(默认), 3=已驳回
 */
@Getter
@Setter
@TableName("sys_book")
public class SysBook implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("author")
    private String author;

    @TableField("description")
    private String description;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("file_path")
    private String filePath;

    @TableField("category")
    private String category;

    @TableField("tags")
    private String tags;

    @TableField("create_time")
    private LocalDateTime createTime;

    /** 上传用户ID，NULL 表示管理员上传 */
    @TableField("uploader_id")
    private Long uploaderId;

    /** 状态：0=私有, 1=待审核, 2=已公开, 3=已驳回 */
    @TableField("status")
    private Integer status;

    /** 虚拟字段：平均评分（不落库） */
    @TableField(exist = false)
    private Double avgRating;

    /** 虚拟字段：热度值（不落库） */
    @TableField(exist = false)
    private Long heat;

    /** 虚拟字段：上传者昵称（不落库，审核列表用） */
    @TableField(exist = false)
    private String uploaderNickname;
}
