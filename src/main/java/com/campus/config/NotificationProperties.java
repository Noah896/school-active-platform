package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.notification")
public class NotificationProperties {
    private String topic = "campus-notification-topic";
    private String consumerGroup = "campus-notification-consumer-group";
}
