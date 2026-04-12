package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.SysComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysCommentMapper extends BaseMapper<SysComment> {

    // 查询某本书的所有评论 (包括子评论，扁平化查询，后续在 Controller 或 Service 组装树形结构)
    @Select("SELECT c.*, " +
            "u.nickname, u.avatar, " +
            "ru.nickname AS reply_nickname, " +
            "EXISTS(SELECT 1 FROM sys_comment_like l WHERE l.comment_id = c.id AND l.user_id = #{currentUserId}) as is_liked " +
            "FROM sys_comment c " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "LEFT JOIN sys_user ru ON c.reply_user_id = ru.id " +
            "WHERE c.book_id = #{bookId} " +
            "ORDER BY c.create_time DESC")
    List<SysComment> selectByBookId(@Param("bookId") Long bookId, @Param("currentUserId") Long currentUserId);
    // === 新增：计算某本书的平均分 ===
    // IFNULL(..., 0) 防止没有评论时返回 null
    // ROUND(..., 1) 保留一位小数，例如 4.5
    @Select("SELECT IFNULL(ROUND(AVG(rating), 1), 0) " +
            "FROM sys_comment " +
            "WHERE book_id = #{bookId} AND rating > 0") // 加上 AND rating > 0
    Double getAvgRating(@Param("bookId") Long bookId);

    // === 新增：查询某用户所有的书籍评论（带书名） ===
    @Select("SELECT c.*, b.title as bookName " +
            "FROM sys_comment c " +
            "LEFT JOIN sys_book b ON c.book_id = b.id " +
            "WHERE c.user_id = #{userId} " +
            "ORDER BY c.create_time DESC")
    List<SysComment> selectUserAllComments(@Param("userId") Long userId);
}