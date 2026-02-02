package com.example.reading.service.impl;

import com.example.reading.entity.BookComment;
import com.example.reading.mapper.BookCommentMapper;
import com.example.reading.service.IBookCommentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 书籍评论表 服务实现类
 * </p>
 *
 * @author CodingAssistant
 * @since 2026-01-31
 */
@Service
public class BookCommentServiceImpl extends ServiceImpl<BookCommentMapper, BookComment> implements IBookCommentService {

}
