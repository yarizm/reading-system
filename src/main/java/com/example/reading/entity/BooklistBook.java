package com.example.reading.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("booklist_book")
public class BooklistBook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long booklistId;
    private Long bookId;
}
