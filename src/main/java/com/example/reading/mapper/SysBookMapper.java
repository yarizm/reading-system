package com.example.reading.mapper;

import com.example.reading.entity.SysBook;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 书籍信息表 Mapper 接口
 * </p>
 *
 * @author CodingAssistant
 * @since 2026-01-31
 */
@Mapper
public interface SysBookMapper extends BaseMapper<SysBook> {

    // ... 其他已有的方法 ...

    /**
     * 查询热度榜 (根据加入书架的人数排序)
     * limit 6: 只取前6本作为轮播展示
     */
    @Select("SELECT b.*, COUNT(s.id) as heat " +
            "FROM sys_book b " +
            "LEFT JOIN user_bookshelf s ON b.id = s.book_id " +
            "GROUP BY b.id " +
            "ORDER BY heat DESC " +
            "LIMIT 6")
    List<SysBook> selectHotBooks();

    // === 新增：根据关键词模糊匹配书籍（用于 AI 推荐回落到本地库） ===
    // 优先匹配书名，其次匹配作者
    @Select("SELECT * FROM sys_book " +
            "WHERE title LIKE CONCAT('%', #{keyword}, '%') " +
            "OR author LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT 1")
    SysBook selectOneByKeyword(@Param("keyword") String keyword);

    // === 新增：随机推荐（当用户没书架或AI失败时的兜底方案） ===
    @Select("SELECT * FROM sys_book ORDER BY RAND() LIMIT 8")
    List<SysBook> selectRandomBooks();
}