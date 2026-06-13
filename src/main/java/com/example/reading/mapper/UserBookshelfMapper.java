package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.UserBookshelf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserBookshelfMapper extends BaseMapper<UserBookshelf> {

    // === 关键：关联查询 ===
    // 查出书架记录，同时把 sys_book 里的书名、封面、作者查出来
    // 按最后阅读时间倒序排列
    @Select("SELECT " +
            " s.id, " +
            " s.user_id AS userId, " +
            " s.book_id AS bookId, " +
            " s.last_read_time AS lastReadTime, " +
            " IFNULL(s.current_chapter_index, 0) AS currentChapterIndex, " +
            " b.title AS bookName, " +
            " b.cover_url AS coverUrl, " +
            " b.author, " +
            " IFNULL(chapter_counts.totalChapters, 0) AS totalChapters, " +
            " current_chapters.title AS currentChapterTitle " +
            "FROM user_bookshelf s " +
            "LEFT JOIN sys_book b ON s.book_id = b.id " +
            "LEFT JOIN (SELECT book_id, COUNT(*) AS totalChapters FROM sys_chapter GROUP BY book_id) chapter_counts " +
            "ON chapter_counts.book_id = b.id " +
            "LEFT JOIN (SELECT book_id, sort, MIN(title) AS title FROM sys_chapter GROUP BY book_id, sort) current_chapters " +
            "ON current_chapters.book_id = b.id AND current_chapters.sort = s.current_chapter_index " +
            "WHERE s.user_id = #{userId} " +
            "ORDER BY s.last_read_time DESC")
    List<Map<String, Object>> selectMyShelf(@Param("userId") Long userId);

    @Select("SELECT " +
            " s.id, " +
            " s.user_id AS userId, " +
            " s.book_id AS bookId, " +
            " s.last_read_time AS lastReadTime, " +
            " IFNULL(s.current_chapter_index, 0) AS currentChapterIndex, " +
            " b.title AS bookName, " +
            " b.cover_url AS coverUrl, " +
            " b.author, " +
            " IFNULL(chapter_counts.totalChapters, 0) AS totalChapters, " +
            " current_chapters.title AS currentChapterTitle " +
            "FROM user_bookshelf s " +
            "JOIN sys_book b ON s.book_id = b.id " +
            "LEFT JOIN (SELECT book_id, COUNT(*) AS totalChapters FROM sys_chapter GROUP BY book_id) chapter_counts " +
            "ON chapter_counts.book_id = b.id " +
            "LEFT JOIN (SELECT book_id, sort, MIN(title) AS title FROM sys_chapter GROUP BY book_id, sort) current_chapters " +
            "ON current_chapters.book_id = b.id AND current_chapters.sort = s.current_chapter_index " +
            "WHERE s.user_id = #{userId} " +
            "AND (b.status = 2 OR b.status IS NULL) " +
            "ORDER BY s.last_read_time DESC")
    List<Map<String, Object>> selectPublicShelf(@Param("userId") Long userId);

    // === 新增：获取用户书架中所有书籍的标题（用于投喂给 AI） ===
    @Select("SELECT b.title FROM user_bookshelf s " +
            "LEFT JOIN sys_book b ON s.book_id = b.id " +
            "WHERE s.user_id = #{userId} ORDER BY s.last_read_time DESC LIMIT 10")
    List<String> selectBookTitlesByUserId(@Param("userId") Long userId);
}
