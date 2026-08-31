package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.activity.register")
public class ActivityRegisterProperties {
    private String topic;
    private String consumerGroup;
}
