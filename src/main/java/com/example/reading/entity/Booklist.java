package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName("booklist")
public class Booklist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String shareCode;
    private LocalDateTime createTime;

    /** 非数据库字段：书单包含的书籍列表（用于前端展示） */
    @TableField(exist = false)
    private List<Map<String, Object>> books;
}
