package com.example.reading.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通知 WebSocket 处理器
 * 客户端连接时通过 URL 参数传入 userId: /ws/notification?userId=123
 * 后端通过 sendNotification() 向指定用户推送消息
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    // userId -> List<WebSocketSession>
    private static final ConcurrentHashMap<Long, List<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            SESSIONS.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            List<WebSocketSession> userSessions = SESSIONS.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    SESSIONS.remove(userId);
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 客户端发来的消息（心跳 ping 等），不做处理
    }

    /**
     * 向指定用户推送通知
     * @param userId 目标用户ID
     * @param type   通知类型: "chat" / "friend_request" / "book_share"
     * @param data   附加数据
     */
    public void sendNotification(Long userId, String type, Map<String, Object> data) {
        List<WebSocketSession> userSessions = SESSIONS.get(userId);
        if (userSessions != null && !userSessions.isEmpty()) {
            try {
                Map<String, Object> payload = new ConcurrentHashMap<>();
                payload.put("type", type);
                payload.put("data", data);
                String msgStr = objectMapper.writeValueAsString(payload);
                TextMessage textMessage = new TextMessage(msgStr);
                
                for (WebSocketSession session : userSessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            userSessions.remove(session);
                        }
                    }
                }
                if (userSessions.isEmpty()) {
                    SESSIONS.remove(userId);
                }
            } catch (Exception e) {
                // Ignore parse errors
            }
        }
    }

    private Long extractUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null && query.contains("userId=")) {
            try {
                String val = query.split("userId=")[1].split("&")[0];
                return Long.parseLong(val);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
