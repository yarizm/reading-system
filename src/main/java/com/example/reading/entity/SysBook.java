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
 * 书籍信息表
 * </p>
 *
 * @author CodingAssistant
 * @since 2026-01-31
 */
@Getter
@Setter
@TableName("sys_book")
public class SysBook implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 书名
     */
    @TableField("title")
    private String title;

    /**
     * 作者
     */
    @TableField("author")
    private String author;

    /**
     * 书籍简介/摘要
     */
    @TableField("description")
    private String description;

    /**
     * 封面图片链接
     */
    @TableField("cover_url")
    private String coverUrl;

    /**
     * 书籍文件存储路径(PDF/TXT)
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 分类(如: 小说, 文献)
     */
    @TableField("category")
    private String category;

    /**
     * AI分析出的标签(用于推荐匹配)
     */
    @TableField("tags")
    private String tags;

    /**
     * 上架时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
    // === 新增字段 (数据库里没有这一列，只是为了传给前端) ===
    @TableField(exist = false)
    private Double avgRating;
    // === 新增：热度值 (数据库不存在此列，仅用于查询返回) ===
    @TableField(exist = false)
    private Long heat;
}
