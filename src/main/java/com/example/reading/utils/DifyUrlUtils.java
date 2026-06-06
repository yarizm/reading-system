package com.example.reading.utils;

/**
 * Dify URL 工具类
 * 处理 DIFY_CHAT_URL 从旧格式（含 /chat-messages）到新格式（基础 URL）的向后兼容。
 */
public class DifyUrlUtils {

    private DifyUrlUtils() {}

    /**
     * 去除 URL 末尾的 /chat-messages 后缀。
     * 旧格式 DIFY_CHAT_URL=https://api.dify.ai/v1/chat-messages
     * 新格式 DIFY_CHAT_URL=https://api.dify.ai/v1
     */
    public static String trimChatMessagesSuffix(String url) {
        if (url == null) return null;
        return url.replaceAll("/chat-messages/?$", "");
    }

    /**
     * 去除 URL 末尾的斜杠，防止拼接时出现双斜杠。
     */
    public static String trimTrailingSlash(String url) {
        if (url == null) return null;
        return url.replaceAll("/+$", "");
    }
}
