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
class GuideControllerTest {

    @Mock
    private AuthContextService authContextService;

    @Mock
    private HttpServletRequest httpRequest;

    // GuideController 通过 WebClient.Builder 构造注入 + @Value/@Autowired 字段注入，
    // 这里手构并把 authContextService 反射注入；边界用例在流式开始前即抛异常，WebClient/globalContextService 不会被触及。
    private GuideController buildController() {
        GuideController controller = new GuideController(WebClient.builder());
        ReflectionTestUtils.setField(controller, "authContextService", authContextService);
        return controller;
    }

    @Test
    void chatRejectsUnauthenticated() {
        when(authContextService.currentUserId(httpRequest)).thenReturn(null);
        GuideController.GuideRequest req = new GuideController.GuideRequest();
        req.query = "推荐一本书";

        assertThatThrownBy(() -> buildController().chat(req, httpRequest))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void chatRejectsNullBody() {
        when(authContextService.currentUserId(httpRequest)).thenReturn(1L);

        assertThatThrownBy(() -> buildController().chat(null, httpRequest))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void chatRejectsBlankQuery() {
        when(authContextService.currentUserId(httpRequest)).thenReturn(1L);
        GuideController.GuideRequest req = new GuideController.GuideRequest();
        req.query = "   ";

        assertThatThrownBy(() -> buildController().chat(req, httpRequest))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
