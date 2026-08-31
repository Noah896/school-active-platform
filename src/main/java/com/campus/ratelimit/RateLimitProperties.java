package com.campus.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "campus.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Map<String, RateLimitRule> scenes = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, RateLimitRule> getScenes() {
        return scenes;
    }

    public void setScenes(Map<String, RateLimitRule> scenes) {
        this.scenes = scenes;
    }
}
