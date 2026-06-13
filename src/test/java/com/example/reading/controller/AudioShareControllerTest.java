package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.ChatMessage;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IChatMessageService;
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
class AudioShareControllerTest {

    @Mock
    private IChatMessageService chatMessageService;

    @Mock
    private NotificationWebSocketHandler notificationHandler;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AudioShareController controller;

    @Test
    void shareAudioRejectsMissingBodyWithoutSavingMessage() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.shareAudio(null, request);

        assertThat(result.getCode()).isEqualTo("500");
        verify(authContextService, never()).areFriends(any(), any());
        verify(chatMessageService, never()).save(any(ChatMessage.class));
    }
}
