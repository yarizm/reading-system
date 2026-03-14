package com.example.reading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.reading.entity.Friendship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FriendshipMapper extends BaseMapper<Friendship> {

    /**
     * 查询用户的好友列表 (status=1)，关联 sys_user 拿昵称/头像
     */
    List<Map<String, Object>> selectFriendsWithUserInfo(@Param("userId") Long userId);

    /**
     * 查询收到的待处理好友请求 (friend_id=userId, status=0)
     */
    List<Map<String, Object>> selectPendingRequests(@Param("userId") Long userId);
}
