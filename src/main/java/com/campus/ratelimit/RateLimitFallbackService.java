package com.campus.ratelimit;

import com.campus.dto.Result;
import com.campus.service.IActivityService;
import org.springframework.stereotype.Service;

@Service
public class RateLimitFallbackService {

    private final IActivityService activityService;

    public RateLimitFallbackService(IActivityService activityService) {
        this.activityService = activityService;
    }

    public Result handle(String scene, RateLimitRule rule, Object[] args) {
        RateLimitFallbackMode fallbackMode = rule == null ? RateLimitFallbackMode.FAIL : rule.getFallbackMode();
        if (fallbackMode == RateLimitFallbackMode.SEARCH_SIMPLIFIED) {
            return activityService.rateLimitFallbackPublicActivities(
                    readString(args, 1),
                    readInteger(args, 8),
                    readInteger(args, 9)
            );
        }
        if (fallbackMode == RateLimitFallbackMode.ACTIVITY_DETAIL_CACHE) {
            return activityService.rateLimitFallbackActivityDetail(readLong(args, 0));
        }
        String message = rule == null ? null : rule.getMessage();
        return Result.fail(message == null || message.isBlank() ? "当前访问人数较多，请稍后重试" : message);
    }

    public boolean shouldApplyRateLimit(String scene, Object[] args) {
        if (!"register".equals(scene)) {
            return true;
        }
        Long activityId = readLong(args, 0);
        return activityId != null && activityService.shouldApplyRegisterRateLimit(activityId);
    }

    private String readString(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        return String.valueOf(args[index]);
    }

    private Integer readInteger(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        Object value = args[index];
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Long readLong(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return null;
        }
        Object value = args[index];
        if (value instanceof Long longValue) {
            return longValue;
        }
        return Long.valueOf(String.valueOf(value));
    }
}
