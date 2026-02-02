package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.SysParagraphComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysParagraphCommentMapper extends BaseMapper<SysParagraphComment> {

    // 关联查询：查评论的同时，带出用户的昵称和头像
    @Select("SELECT c.*, u.nickname, u.avatar, " +
            "EXISTS(SELECT 1 FROM sys_paragraph_like l WHERE l.comment_id = c.id AND l.user_id = #{currentUserId}) as is_liked " +
            "FROM sys_paragraph_comment c " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "WHERE c.book_id = #{bookId} " +
            "AND c.chapter_index = #{chapterIndex} " +
            "AND c.paragraph_index = #{paragraphIndex} " +
            "ORDER BY c.like_count DESC, c.create_time DESC")
    List<SysParagraphComment> selectByPositionWithLike(@Param("bookId") Long bookId,
                                                       @Param("chapterIndex") Integer chapterIndex,
                                                       @Param("paragraphIndex") Integer paragraphIndex,
                                                       @Param("currentUserId") Long currentUserId);

    // 查询某用户在这本书的所有评论
    @Select("SELECT * FROM sys_paragraph_comment WHERE user_id = #{userId} AND book_id = #{bookId} ORDER BY create_time DESC")
    List<SysParagraphComment> selectUserComments(@Param("userId") Long userId, @Param("bookId") Long bookId);
}