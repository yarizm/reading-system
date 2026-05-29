package com.example.reading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.AiGeneratedContent;
import com.example.reading.mapper.AiGeneratedContentMapper;
import com.example.reading.service.IAiGeneratedContentService;
import org.springframework.stereotype.Service;

@Service
public class AiGeneratedContentServiceImpl extends ServiceImpl<AiGeneratedContentMapper, AiGeneratedContent> implements IAiGeneratedContentService {
}
