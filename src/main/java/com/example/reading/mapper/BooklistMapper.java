package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.Booklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BooklistMapper extends BaseMapper<Booklist> {

    /**
     * 查询某个书单下的所有书籍信息
     */
    @Select("SELECT b.id, b.title, b.author, b.cover_url AS coverUrl, b.category " +
            "FROM booklist_book bb " +
            "LEFT JOIN sys_book b ON bb.book_id = b.id " +
            "WHERE bb.booklist_id = #{booklistId}")
    List<Map<String, Object>> selectBooksByListId(@Param("booklistId") Long booklistId);
}
