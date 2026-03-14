package com.example.reading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.ChatMessage;
import com.example.reading.mapper.ChatMessageMapper;
import com.example.reading.service.IChatMessageService;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {
}
