package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus.activity.search")
public class ActivitySearchProperties {

    private Sync sync = new Sync();
    private Es es = new Es();

    @Data
    public static class Sync {
        private String topic;
        private String consumerGroup;
    }

    @Data
    public static class Es {
        private boolean enabled = true;
        private String uris;
        private String username;
        private String password;
        private String indexName = "activity_index_dev";
        private int connectTimeoutMillis = 2000;
        private int socketTimeoutMillis = 3000;
        private String repairCron = "0 */10 * * * ?";
        private int repairLookbackHours = 24;
        private int repairPageSize = 200;
    }
}
