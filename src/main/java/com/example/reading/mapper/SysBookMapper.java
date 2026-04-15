package com.example.reading.mapper;

import com.example.reading.entity.SysBook;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 书籍 Mapper
 */
@Mapper
public interface SysBookMapper extends BaseMapper<SysBook> {

    /** 热门书籍（仅已公开，按书架收藏人数排序，取前 6） */
    @Select("SELECT b.*, COUNT(s.id) as heat " +
            "FROM sys_book b " +
            "LEFT JOIN user_bookshelf s ON b.id = s.book_id " +
            "WHERE (b.status = 2 OR b.status IS NULL) " +
            "GROUP BY b.id " +
            "ORDER BY heat DESC " +
            "LIMIT 6")
    List<SysBook> selectHotBooks();

    /** 排行榜（仅已公开，按书架收藏人数排序，取前 10） */
    @Select("SELECT b.*, COUNT(s.id) as heat " +
            "FROM sys_book b " +
            "LEFT JOIN user_bookshelf s ON b.id = s.book_id " +
            "WHERE (b.status = 2 OR b.status IS NULL) " +
            "GROUP BY b.id " +
            "ORDER BY heat DESC, b.id DESC " +
            "LIMIT 10")
    List<SysBook> selectRankBooks();

    /** 随机推荐（仅已公开） */
    @Select("SELECT * FROM sys_book WHERE (status = 2 OR status IS NULL) ORDER BY RAND() LIMIT 8")
    List<SysBook> selectRandomBooks();

    /** 待审核书籍列表（含上传者昵称） */
    @Select("SELECT b.*, u.nickname as uploaderNickname " +
            "FROM sys_book b " +
            "LEFT JOIN sys_user u ON b.uploader_id = u.id " +
            "WHERE b.status = 1 " +
            "ORDER BY b.create_time DESC")
    List<SysBook> selectPendingBooks();

    /** 用户上传的书籍列表 */
    @Select("SELECT * FROM sys_book WHERE uploader_id = #{userId} ORDER BY create_time DESC")
    List<SysBook> selectByUploaderId(@Param("userId") Long userId);
}