package com.example.reading.dto;

import lombok.Data;

@Data
public class UserDto {
    private String username;
    private String password;
    private String nickname; // 注册时可能用到
    private Integer age;     // 注册时可能用到
}