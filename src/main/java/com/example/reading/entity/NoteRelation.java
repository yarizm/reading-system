package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("note_relation")
public class NoteRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @com.baomidou.mybatisplus.annotation.TableField("note_id_1")
    private Long noteId1;
    
    @com.baomidou.mybatisplus.annotation.TableField("note_id_2")
    private Long noteId2;
    
    private LocalDateTime createTime;
}
