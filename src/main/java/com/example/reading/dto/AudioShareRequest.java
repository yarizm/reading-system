package com.example.reading.dto;

import lombok.Data;

@Data
public class AudioShareRequest {
    private Long senderId;
    private Long receiverId;
    private String audioUrl;
    private String title;
    private String sourceType;
    private Long bookId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private String message;
}
