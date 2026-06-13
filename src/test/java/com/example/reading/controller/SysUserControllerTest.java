package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.dto.UserDto;
import com.example.reading.entity.SysUser;
import com.example.reading.mapper.UserBookshelfMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.AuthTokenService;
import com.example.reading.service.ISysUserService;
import com.example.reading.service.RateLimitService;
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
class SysUserControllerTest {

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private UserBookshelfMapper shelfMapper;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private SysUserController controller;

    @Test
    void loginRejectsMissingBodyWithoutCallingLoginService() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isAllowed("login:127.0.0.1", 10, 60)).thenReturn(true);

        Result<SysUser> result = controller.login(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(sysUserService, never()).login(any(UserDto.class));
    }

    @Test
    void updateRejectsMissingBodyWithoutLoadingUser() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.update(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(sysUserService, never()).getById(any());
    }

    @Test
    void adminUpdateRejectsMissingBodyWithoutLoadingTarget() {
        when(authContextService.currentUserId(request)).thenReturn(1L);
        when(authContextService.isAdmin(request)).thenReturn(true);

        Result<?> result = controller.adminUpdate(null, null, request);

        assertThat(result.getCode()).isEqualTo("500");
        verify(sysUserService, never()).getById(any());
    }

    @Test
    void updatePasswordRejectsMissingBodyWithoutLoadingUser() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.updatePassword(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(sysUserService, never()).getById(any());
    }
}
