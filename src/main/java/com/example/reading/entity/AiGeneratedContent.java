package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_generated_content")
public class AiGeneratedContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String contentType;
    
    private String referenceType;
    
    private Long referenceId;
    
    private String title;
    
    private String content;
    
    private String metadata;
    
    private LocalDateTime createTime;
}
