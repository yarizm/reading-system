package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.BookShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BookShareMapper extends BaseMapper<BookShare> {

    /**
     * 查询用户收到的书籍分享 (join sys_book + sys_user)
     */
    List<Map<String, Object>> selectSharesWithBookInfo(@Param("userId") Long userId);
}
