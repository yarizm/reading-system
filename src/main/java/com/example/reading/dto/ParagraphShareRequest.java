package com.example.reading.dto;

import lombok.Data;

@Data
public class ParagraphShareRequest {
    private Long senderId;
    private Long receiverId;
    private Long bookId;
    private Integer chapterIndex;
    private Integer paragraphIndex;
    private String quote;
    private String message;
}
