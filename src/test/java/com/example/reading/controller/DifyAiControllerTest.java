package com.example.reading.controller;

import com.example.reading.service.AuthContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DifyAiControllerTest {

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest httpRequest;

    private DifyAiController buildController() {
        DifyAiController controller = new DifyAiController(WebClient.builder());
        ReflectionTestUtils.setField(controller, "authContextService", authContextService);
        return controller;
    }

    @Test
    void analyzeRejectsUnauthenticated() {
        when(authContextService.currentUserId(httpRequest)).thenReturn(null);
        DifyAiController.AskRequest req = new DifyAiController.AskRequest();
        req.text = "选中文本";
        req.mode = "explain";

        // 未登录在创建 SseEmitter 前即抛 403，无需触及 WebClient/Dify
        assertThatThrownBy(() -> buildController().analyzeText(req, httpRequest))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
