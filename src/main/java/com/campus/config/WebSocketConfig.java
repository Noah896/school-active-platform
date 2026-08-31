package com.campus.config;

import com.campus.websocket.ActivityRegistrationWebSocketHandler;
import com.campus.websocket.ActivityRegistrationWebSocketInterceptor;
import com.campus.websocket.NotificationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import jakarta.annotation.Resource;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ActivityRegistrationWebSocketHandler activityRegistrationWebSocketHandler;

    @Resource
    private ActivityRegistrationWebSocketInterceptor activityRegistrationWebSocketInterceptor;

    @Resource
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(activityRegistrationWebSocketHandler, "/ws/activity-registration")
                .addInterceptors(activityRegistrationWebSocketInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(notificationWebSocketHandler, "/ws/notification")
                .addInterceptors(activityRegistrationWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
