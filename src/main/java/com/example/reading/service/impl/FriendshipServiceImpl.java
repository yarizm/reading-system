package com.example.reading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.reading.entity.Friendship;
import com.example.reading.mapper.FriendshipMapper;
import com.example.reading.service.IFriendshipService;
import org.springframework.stereotype.Service;

@Service
public class FriendshipServiceImpl extends ServiceImpl<FriendshipMapper, Friendship> implements IFriendshipService {
}
