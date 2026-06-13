package com.example.reading.controller;

import com.example.reading.entity.AiGeneratedContent;
import com.example.reading.service.AuthContextService;
import com.example.reading.service.DifyWorkflowClient;
import com.example.reading.service.IAiGeneratedContentService;
import com.example.reading.service.ISysBookService;
import com.example.reading.service.NoteAiService;
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
class NoteAiControllerTest {

    @Mock
    private DifyWorkflowClient difyWorkflowClient;

    @Mock
    private AuthContextService authContextService;

    @Mock
    private NoteAiService noteAiService;

    @Mock
    private IAiGeneratedContentService aiGeneratedContentService;

    @Mock
    private ISysBookService sysBookService;

    @Mock
    private HttpServletRequest request;

    private NoteAiController controller;

    @BeforeEach
    void setUp() {
        controller = new NoteAiController(difyWorkflowClient, "http://dify.example", "key");
        ReflectionTestUtils.setField(controller, "authContextService", authContextService);
        ReflectionTestUtils.setField(controller, "noteAiService", noteAiService);
        ReflectionTestUtils.setField(controller, "aiGeneratedContentService", aiGeneratedContentService);
        ReflectionTestUtils.setField(controller, "sysBookService", sysBookService);
    }

    @Test
    void runNoteWorkflowRejectsMissingBodyBeforeCallingDify() {
        when(authContextService.currentUserId(request)).thenReturn(7L);

        assertThatThrownBy(() -> controller.runNoteWorkflow(3L, null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(400));

        verify(difyWorkflowClient, never()).runBlocking(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Map.class),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void runNoteWorkflowRejectsUnsupportedActionBeforeCallingDify() {
        NoteAiController.NoteAiRequest body = new NoteAiController.NoteAiRequest();
        body.action = "unknown";
        when(authContextService.currentUserId(request)).thenReturn(7L);

        assertThatThrownBy(() -> controller.runNoteWorkflow(3L, body, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(400));

        verify(difyWorkflowClient, never()).runBlocking(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Map.class),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getResultRejectsOwnerlessGeneratedContentWithoutThrowingNullPointer() {
        AiGeneratedContent content = new AiGeneratedContent();
        content.setId(5L);

        when(authContextService.currentUserId(request)).thenReturn(7L);
        when(aiGeneratedContentService.getById(5L)).thenReturn(content);

        assertThatThrownBy(() -> controller.getResult(5L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }
}
