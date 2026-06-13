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

    /** 公开书籍总数 */
    @Select("SELECT COUNT(*) FROM sys_book WHERE (status = 2 OR status IS NULL)")
    Long countPublicBooks();

    /** 公开书籍分页窗口，用于推荐兜底随机偏移取样 */
    @Select("SELECT * FROM sys_book WHERE (status = 2 OR status IS NULL) ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<SysBook> selectPublicBooksWindow(@Param("limit") int limit, @Param("offset") long offset);

    /** 用户上传的书籍列表 */
    @Select("SELECT * FROM sys_book WHERE uploader_id = #{userId} ORDER BY create_time DESC")
    List<SysBook> selectByUploaderId(@Param("userId") Long userId);
}
