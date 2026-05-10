package com.example.reading.dto;

import lombok.Getter;
import lombok.Setter;

/** 用户编辑书籍时允许修改的字段白名单 */
@Getter
@Setter
public class BookEditDTO {
    private Long id;
    private String title;
    private String author;
    private String category;
    private String description;
    private String tags;
    private String coverUrl;
    private String filePath;
}
