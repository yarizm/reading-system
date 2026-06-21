package com.example.reading.util;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DifyResponseUtilTest {

    @Test
    void successResponseWithCodeInOutputs_isNotError() {
        // 模拟 outputs 里恰好有个 code 字段的成功响应
        Map<String, Object> response = Map.of(
                "workflow_run_id", "run-1",
                "data", Map.of(
                        "status", "succeeded",
                        "outputs", Map.of("code", 200, "result", "ok")
                )
        );
        assertFalse(DifyResponseUtil.isError(response), "带 data 的响应不应被判为错误");
    }

    @Test
    void topLevelErrorResponse_isError() {
        Map<String, Object> response = Map.of(
                "code", "invalid_param",
                "message", "参数校验失败",
                "status", 400
        );
        assertTrue(DifyResponseUtil.isError(response));
        assertEquals("参数校验失败", DifyResponseUtil.getErrorMessage(response));
    }

    @Test
    void responseWithoutCode_isNotError() {
        Map<String, Object> response = Map.of("data", Map.of("status", "succeeded"));
        assertFalse(DifyResponseUtil.isError(response));
    }
}
