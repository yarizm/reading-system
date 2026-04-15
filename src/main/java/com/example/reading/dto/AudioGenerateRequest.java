package com.example.reading.dto;

import lombok.Data;

@Data
public class AudioGenerateRequest {
    private String text;
    private String voice;
    private Long bookId;
    private Long chapterId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private String title;
    private String sourceType;
}
