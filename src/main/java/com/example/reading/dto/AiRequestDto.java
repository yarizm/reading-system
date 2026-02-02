package com.example.reading.dto;
import lombok.Data;

@Data
public class AiRequestDto {
    private String text;
    private String type;
    private Long bookId;
    private Long userId;
}