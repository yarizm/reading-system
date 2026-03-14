package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 获取两人之间的聊天记录 (按时间升序)
     */
    List<ChatMessage> selectChatHistory(@Param("userId") Long userId,
                                         @Param("friendId") Long friendId);

    /**
     * 获取用户的未读消息总数
     */
    Long selectUnreadCount(@Param("userId") Long userId);

    /**
     * 获取用户的会话列表 (最近联系人 + 最近一条消息 + 未读数)
     */
    List<Map<String, Object>> selectConversations(@Param("userId") Long userId);
}
