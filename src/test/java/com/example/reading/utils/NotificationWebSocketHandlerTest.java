package com.example.reading.utils;

import com.example.reading.service.AuthContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandlerTest {

    @Mock
    private AuthContextService authContextService;

    @Mock
    private WebSocketSession session;

    @Test
    void acceptsEncodedTokenRegardlessOfQueryParameterOrder() throws Exception {
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler();
        ReflectionTestUtils.setField(handler, "authContextService", authContextService);
        Map<String, Object> attributes = new HashMap<>();

        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/notification?foo=bar&token=token%20with%20space&userId=7"));
        when(session.getAttributes()).thenReturn(attributes);
        when(authContextService.currentUserId("token with space")).thenReturn(7L);

        handler.afterConnectionEstablished(session);

        assertThat(attributes).containsEntry("userId", 7L);
        verify(session, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void rejectsMismatchedTokenUser() throws Exception {
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler();
        ReflectionTestUtils.setField(handler, "authContextService", authContextService);

        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/notification?userId=7&token=token-8"));
        when(authContextService.currentUserId("token-8")).thenReturn(8L);

        handler.afterConnectionEstablished(session);

        verify(session).close(org.mockito.ArgumentMatchers.argThat(status ->
                status.getCode() == CloseStatus.NOT_ACCEPTABLE.getCode()
                        && "Unauthorized".equals(status.getReason())));
    }
}
