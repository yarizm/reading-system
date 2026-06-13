package com.example.reading.controller;

import com.example.reading.common.Result;
import com.example.reading.entity.BookShare;
import com.example.reading.entity.ChatMessage;
import com.example.reading.mapper.BookShareMapper;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.IBookShareService;
import com.example.reading.service.IChatMessageService;
import com.example.reading.service.ISysBookService;
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
class BookShareControllerTest {

    @Mock
    private IBookShareService bookShareService;

    @Mock
    private IChatMessageService chatMessageService;

    @Mock
    private BookShareMapper bookShareMapper;

    @Mock
    private NotificationWebSocketHandler notificationHandler;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private BookShareController controller;

    @Test
    void shareBookRejectsMissingBodyWithoutSavingShare() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        Result<?> result = controller.shareBook(null, request);

        assertThat(result.getCode()).isEqualTo("403");
        verify(authContextService, never()).areFriends(any(), any());
        verify(bookShareService, never()).save(any(BookShare.class));
        verify(chatMessageService, never()).save(any(ChatMessage.class));
    }
}
