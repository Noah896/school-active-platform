package com.campus.websocket;

import cn.hutool.json.JSONUtil;
import com.campus.dto.NotificationPushDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NotificationSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        sessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.getOrDefault(userId, Collections.emptySet());
        if (session == null) {
            userSessions.clear();
        } else {
            userSessions.remove(session);
        }
        if (userSessions.isEmpty()) {
            sessions.remove(userId);
        }
    }

    public void push(Long userId, NotificationPushDTO payload) {
        if (userId == null || payload == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        String message = JSONUtil.toJsonStr(payload);
        for (WebSocketSession session : userSessions) {
            if (session == null || !session.isOpen()) {
                userSessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.warn("通知推送失败 userId={}", userId, e);
            }
        }
    }
}
