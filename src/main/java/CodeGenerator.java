import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

/**
 * 代码生成器
 * 作用：根据数据库表结构，自动生成 Java 代码
 * 使用后可以删除本类
 */
public class CodeGenerator {

    public static void main(String[] args) {
        // 1. 数据库配置
        String url = "jdbc:mysql://localhost:3306/smartreader?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        String username = "root";       // TODO: 确认你的数据库账号
        String password = "yarizm75";   // TODO: 确认你的数据库密码

        // 2. 路径配置
        // 获取当前项目根目录
        String projectPath = System.getProperty("user.dir");

        // 3. 开始构建
        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> {
                    builder.author("CodingAssistant") // 设置作者
                            .outputDir(projectPath + "/src/main/java") // 指定 Java 代码输出目录
                            .disableOpenDir(); // 生成后不自动打开文件夹
                })
                .packageConfig(builder -> {
                    builder.parent("com.example.reading") // 设置父包名
                            // 设置 Mapper XML 文件生成到 resources/mapper 目录下
                            .pathInfo(Collections.singletonMap(OutputFile.xml, projectPath + "/src/main/resources/mapper"));
                })
                .strategyConfig(builder -> {
                    builder.addInclude("sys_user", "sys_book", "user_bookshelf", "book_comment", "ai_interaction_log") // 设置需要生成的表名
                            // Entity 实体类策略
                            .entityBuilder()
                            .enableLombok() // 自动添加 @Data 注解
                            .enableTableFieldAnnotation() // 自动添加 @TableField 注解

                            // Controller 控制器策略
                            .controllerBuilder()
                            .enableRestStyle(); // 开启 @RestController (返回 JSON)
                })
                .templateEngine(new VelocityTemplateEngine()) // 使用 Velocity 引擎
                .execute();

        System.out.println("====== 代码生成完成！请刷新项目目录 ======");
    }
}