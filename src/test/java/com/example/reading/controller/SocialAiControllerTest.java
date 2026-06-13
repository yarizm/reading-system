package com.example.reading.controller;

import com.example.reading.service.AuthContextService;
import com.example.reading.service.DifyWorkflowClient;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.ISysNoteService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAiControllerTest {

    @Mock
    private DifyWorkflowClient difyWorkflowClient;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private ISysNoteService sysNoteService;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private HttpServletRequest request;

    private SocialAiController controller;

    @BeforeEach
    void setUp() {
        controller = new SocialAiController(difyWorkflowClient);
        ReflectionTestUtils.setField(controller, "authContextService", authContextService);
        ReflectionTestUtils.setField(controller, "sysNoteService", sysNoteService);
        ReflectionTestUtils.setField(controller, "sysBookService", sysBookService);
        ReflectionTestUtils.setField(controller, "difyWorkflowUrl", "http://dify.example");
        ReflectionTestUtils.setField(controller, "difyApiKey", "key");
    }

    @Test
    void draftReviewRejectsMissingBodyBeforeCallingDify() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        assertThatThrownBy(() -> controller.draftReview(null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(400));

        verify(difyWorkflowClient, never()).runBlocking(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Map.class),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void draftShareRejectsMissingBodyBeforeCallingDify() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        assertThatThrownBy(() -> controller.draftShare(null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(400));

        verify(difyWorkflowClient, never()).runBlocking(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Map.class),
                org.mockito.ArgumentMatchers.anyString());
    }
}
