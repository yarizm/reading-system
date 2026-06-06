package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("note_review")
public class NoteReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long noteId;
    private Float easeFactor;
    private Integer intervalDays;
    private Integer repetitions;
    private LocalDate nextReviewDate;
    private LocalDateTime lastReviewTime;
    private LocalDateTime createTime;
}
