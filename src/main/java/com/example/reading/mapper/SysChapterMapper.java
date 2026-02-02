package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.SysChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysChapterMapper extends BaseMapper<SysChapter> {
    // 只查目录（不查内容，提高性能）
    @Select("SELECT id, book_id, title, word_count, sort FROM sys_chapter WHERE book_id = #{bookId} ORDER BY sort ASC")
    List<SysChapter> selectCatalog(Long bookId);
}