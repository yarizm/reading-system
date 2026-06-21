package com.example.reading.util;

import java.util.Map;

/**
 * Dify 阻塞响应统一判别工具。
 * 背景：Dify 成功响应（data.outputs 内）和错误响应（顶层）都可能含 "code" 字段，
 * 仅凭 containsKey("code") 判断会产生误报。唯一可靠标志是顶层是否存在 "data"。
 */
public class DifyResponseUtil {

    /** 顶层有 code 且没有 data，才是真正的 Dify 错误响应。 */
    public static boolean isError(Map<String, Object> response) {
        if (response == null) {
            return true; // 空响应视为异常，按需调整
        }
        return response.containsKey("code") && !response.containsKey("data");
    }

    public static String getErrorCode(Map<String, Object> response) {
        Object code = response.get("code");
        return code == null ? "UNKNOWN" : String.valueOf(code);
    }

    public static String getErrorMessage(Map<String, Object> response) {
        Object msg = response.get("message");
        return msg == null ? "Dify 调用失败，未返回 message" : String.valueOf(msg);
    }

    /** 成功时安全取出 data 节点，避免后续代码到处做空判断。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getData(Map<String, Object> response) {
        Object data = response.get("data");
        return data instanceof Map ? (Map<String, Object>) data : Map.of();
    }
}
