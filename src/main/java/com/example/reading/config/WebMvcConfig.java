package com.example.reading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射本地文件目录到 /files/** 路径
        // 例如：访问 http://localhost:8090/files/1.jpg -> 读取 D:/reading-system-files/1.jpg
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadPath);
    }
}
