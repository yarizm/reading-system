package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_chapter")
public class SysChapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private String title;
    private String content;
    private Integer wordCount;
    private Integer sort; // 排序号，0, 1, 2...
}