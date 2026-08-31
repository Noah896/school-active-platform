package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.embedding")
public class EmbeddingProperties {

    private boolean enabled = true;
    private String model = "text-embedding-v4";
    private int dimensions = 1024;
    private int behaviorWindowDays = 30;
    private int taskBatchSize = 20;
    private int taskRetryLimit = 3;
    private String compensationCron = "0 */2 * * * ?";
    private String taskName = "campus-embedding-task";
}
