package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.activity.ai-review")
public class ActivityAiReviewProperties {

    private boolean enabled = true;
    private String promptVersion = "v1";
    private int connectTimeoutMillis = 2000;
    private int readTimeoutMillis = 5000;
    private int maxRetries = 1;
    private String topic = "activity-ai-review-topic";
    private String consumerGroup = "activity-ai-review-consumer-group";
    private String compensationCron = "0 */3 * * * ?";
}
