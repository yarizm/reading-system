package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.BookReviewRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BookReviewRequestMapper extends BaseMapper<BookReviewRequest> {

    /** 用户查看自己的所有审核请求（含书名） */
    @Select("SELECT r.*, b.title as book_title, b.author as book_author, b.cover_url as book_cover_url, " +
            "b.category as book_category, b.file_path as book_file_path " +
            "FROM book_review_request r " +
            "LEFT JOIN sys_book b ON r.book_id = b.id " +
            "WHERE r.user_id = #{userId} " +
            "ORDER BY r.create_time DESC")
    List<BookReviewRequest> selectByUserId(@Param("userId") Long userId);

    /** 管理员查看所有待审核请求（含书名、上传者昵称） */
    @Select("SELECT r.*, b.title as book_title, b.author as book_author, b.cover_url as book_cover_url, " +
            "b.description as book_description, b.category as book_category, b.file_path as book_file_path, " +
            "u.nickname as uploader_nickname " +
            "FROM book_review_request r " +
            "LEFT JOIN sys_book b ON r.book_id = b.id " +
            "LEFT JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.status = 0 " +
            "ORDER BY r.create_time DESC")
    List<BookReviewRequest> selectPendingAll();

    /** 查询某书的待审核请求 */
    @Select("SELECT * FROM book_review_request WHERE book_id = #{bookId} AND status = 0")
    List<BookReviewRequest> selectPendingByBookId(@Param("bookId") Long bookId);
}
