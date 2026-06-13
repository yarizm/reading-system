package com.example.reading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.reading.common.Result;
import com.example.reading.entity.Friendship;
import com.example.reading.mapper.FriendshipMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IFriendshipService;
import com.example.reading.service.ISysUserService;
import com.example.reading.utils.NotificationWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendControllerTest {

    @Mock
    private IFriendshipService friendshipService;

    @Mock
    private FriendshipMapper friendshipMapper;

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private NotificationWebSocketHandler notificationHandler;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private FriendController controller;

    @Test
    void sendRequestRejectsMissingBodyWithoutSavingFriendship() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.sendRequest(null, request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(friendshipService, never()).getOne(any(QueryWrapper.class));
        verify(friendshipService, never()).save(any(Friendship.class));
    }
}
