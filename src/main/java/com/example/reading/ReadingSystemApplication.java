package com.example.reading;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// 这一行非常重要：它告诉系统去哪里找数据库操作接口 (Mapper)
// 如果你之后运行代码生成器，Mapper 默认会放在 mapper 包下
@MapperScan("com.example.reading.mapper")
public class ReadingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadingSystemApplication.class, args);
        System.out.println("====== 智能书籍阅读系统启动成功 ======");
    }
}