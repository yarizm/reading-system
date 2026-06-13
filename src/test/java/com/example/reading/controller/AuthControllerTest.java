package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.SysUser;
import com.example.reading.service.AuthService;
import com.example.reading.service.AuthTokenService;
import com.example.reading.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthController controller;

    @Test
    void sendCodeRejectsMissingBodyWithoutConsumingRateLimit() {
        Result<?> result = controller.sendCode(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(rateLimitService, never()).isAllowed(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(authService, never()).generateAndCacheCode(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void sendCodeAcceptsStringTypeParameter() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isAllowed("sendCode:user@example.com", 1, 60)).thenReturn(true);
        when(rateLimitService.isAllowed("sendCode:ip:127.0.0.1", 5, 60)).thenReturn(true);
        when(authService.generateAndCacheCode("user@example.com", 1)).thenReturn("123456");

        Result<?> result = controller.sendCode(Map.of("target", "user@example.com", "type", "1"), request);

        assertThat(result.getCode()).isEqualTo("200");
        verify(authService).generateAndCacheCode("user@example.com", 1);
    }

    @Test
    void registerAcceptsAgeAsNumericString() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isAllowed("register:target:user@example.com", 5, 300)).thenReturn(true);
        when(rateLimitService.isAllowed("register:ip:127.0.0.1", 20, 300)).thenReturn(true);

        Result<?> result = controller.register(
                Map.of(
                        "target", "user@example.com",
                        "code", "123456",
                        "password", "secret",
                        "nickname", "Reader",
                        "age", "18"
                ),
                request
        );

        assertThat(result.getCode()).isEqualTo("200");
        verify(authService).register("user@example.com", "123456", "secret", "Reader", 18);
        verify(rateLimitService).reset("register:target:user@example.com");
    }

    @Test
    void loginByCodeRejectsMissingBodyBeforeRateLimit() {
        Result<?> result = controller.loginByCode(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(rateLimitService, never()).isAllowed(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(authService, never()).loginByCode(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loginByCodeAcceptsNumericCodeAndClearsPassword() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setPassword("hashed");

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitService.isAllowed("loginByCode:127.0.0.1", 10, 60)).thenReturn(true);
        when(authService.loginByCode("user@example.com", "123456")).thenReturn(user);
        when(authTokenService.createToken(9L)).thenReturn("token-9");

        Result<?> result = controller.loginByCode(
                Map.of("target", "user@example.com", "code", 123456),
                request
        );

        assertThat(result.getCode()).isEqualTo("200");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getToken()).isEqualTo("token-9");
    }

    @Test
    void resetPasswordRejectsMissingBodyBeforeRateLimit() {
        Result<?> result = controller.resetPassword(null, request);

        assertThat(result.getCode()).isEqualTo("400");
        verify(rateLimitService, never()).isAllowed(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(authService, never()).resetPassword(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
