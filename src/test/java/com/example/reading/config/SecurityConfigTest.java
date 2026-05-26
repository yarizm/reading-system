package com.example.reading.config;

import com.example.reading.service.AuthContextService;
import com.example.reading.service.AuthTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthTokenService authTokenService;

    @MockBean
    private AuthContextService authContextService;

    @Test
    void protectedEndpointsRequireAuthenticationByDefault() throws Exception {
        mockMvc.perform(post("/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicGetBookListEndpointRemainsAvailable() throws Exception {
        mockMvc.perform(get("/sysBook/list"))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestController {
        @PostMapping("/protected")
        String protectedEndpoint() {
            return "ok";
        }

        @GetMapping("/sysBook/list")
        String publicEndpoint() {
            return "ok";
        }
    }
}
