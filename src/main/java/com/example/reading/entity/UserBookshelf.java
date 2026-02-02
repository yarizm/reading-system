package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_bookshelf")
public class UserBookshelf {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long bookId;
    private LocalDateTime lastReadTime;
    private Integer progressIndex; // 阅读进度 (例如第几段，或者滚动条位置)
    private Integer isFinished;    // 0:未读完 1:已读完
    private Integer currentChapterIndex;
}