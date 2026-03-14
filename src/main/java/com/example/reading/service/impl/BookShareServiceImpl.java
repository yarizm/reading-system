package com.example.reading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.BookShare;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.service.IBookShareService;
import org.springframework.stereotype.Service;

@Service
public class BookShareServiceImpl extends ServiceImpl<BookShareMapper, BookShare> implements IBookShareService {
}
