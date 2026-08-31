package com.campus.ratelimit;

import com.campus.dto.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimitManager rateLimitManager;
    private final RateLimitFallbackService fallbackService;

    public RateLimitAspect(RateLimitManager rateLimitManager, RateLimitFallbackService fallbackService) {
        this.rateLimitManager = rateLimitManager;
        this.fallbackService = fallbackService;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        RateLimitRule rule = rateLimitManager.getRule(rateLimit.scene());
        if (rule == null || !rule.isEnabled() || !fallbackService.shouldApplyRateLimit(rateLimit.scene(), joinPoint.getArgs())
                || rateLimitManager.tryAcquire(rateLimit.scene(), rule)) {
            return joinPoint.proceed();
        }
        if (Result.class.isAssignableFrom(((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getReturnType())) {
            return fallbackService.handle(rateLimit.scene(), rule, joinPoint.getArgs());
        }
        throw new IllegalStateException("Rate limit fallback only supports Result return type");
    }
}
