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
    private Long noteId1;
    private Long noteId2;
    private LocalDateTime createTime;
}
