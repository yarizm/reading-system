package com.example.reading.dto;

import lombok.Data;

@Data
public class AudioGenerateResponse {
    private String audioUrl;
    private String title;
    private String sourceType;
    private Long bookId;
    private Long chapterId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private Boolean cached;
}
