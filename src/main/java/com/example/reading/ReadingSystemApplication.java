package com.example.reading;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.reading.mapper")
public class ReadingSystemApplication {

    private static final Logger log = LoggerFactory.getLogger(ReadingSystemApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ReadingSystemApplication.class, args);
        log.info("Reading system started successfully");
    }
}
