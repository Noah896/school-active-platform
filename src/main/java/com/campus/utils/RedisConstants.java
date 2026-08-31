package com.campus.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final String CACHE_ACTIVITY_DETAIL_KEY = "cache:activity:detail:";
    public static final Long CACHE_ACTIVITY_DETAIL_TTL = 10L;
    public static final String CACHE_ACTIVITY_LIST_KEY = "cache:activity:list:";
    public static final Long CACHE_ACTIVITY_LIST_TTL = 1L;
    public static final String CACHE_ACTIVITY_CATEGORIES_KEY = "activity:category:tree";
    public static final Long CACHE_ACTIVITY_CATEGORIES_TTL = 30L;
    public static final String CACHE_ACTIVITY_USER_STATE_KEY = "cache:activity:user-state:";
    public static final Long CACHE_ACTIVITY_USER_STATE_TTL = 5L;
    public static final String CACHE_ACTIVITY_CHECK_IN_STATS_KEY = "cache:activity:check-in:stats:";
    public static final Long CACHE_ACTIVITY_CHECK_IN_STATS_TTL = 5L;
    public static final String CACHE_ACTIVITY_CHECK_IN_DASHBOARD_KEY = "cache:activity:check-in:dashboard:";
    public static final Long CACHE_ACTIVITY_CHECK_IN_DASHBOARD_TTL = 10L;
    public static final String CACHE_ACTIVITY_CHECK_IN_RECORDS_KEY = "cache:activity:check-in:records:";
    public static final Long CACHE_ACTIVITY_CHECK_IN_RECORDS_TTL = 3L;
    public static final String CACHE_DISCOVER_POST_PAGE_KEY = "cache:discover:posts:";
    public static final Long CACHE_DISCOVER_POST_PAGE_TTL = 1L;
    public static final String CACHE_DISCOVER_POST_DETAIL_KEY = "cache:discover:post:";
    public static final String DISCOVER_POST_LIKED_KEY = "discover:post:liked:";
    public static final String DISCOVER_POST_LIKE_COUNT_KEY = "discover:post:like-count:";
    public static final String ACTIVITY_META_KEY = "activity:meta:";
    public static final String ACTIVITY_STOCK_KEY = "activity:stock:";
    public static final String ACTIVITY_FROZEN_KEY = "activity:frozen:";
    public static final String ACTIVITY_REGISTER_USERS_KEY = "activity:register:users:";
    public static final String ACTIVITY_USER_REGISTER_STATE_KEY = "activity:user:register:";
    public static final Long ACTIVITY_USER_REGISTER_STATE_TTL_HOURS = 12L;
    public static final String ACTIVITY_VOUCHER_DISPLAY_KEY = "activity:voucher:display:";
    public static final String ACTIVITY_CHECK_IN_IDEMPOTENCY_KEY = "activity:checkin:idempotency:";
    public static final Long ACTIVITY_CHECK_IN_IDEMPOTENCY_TTL_MINUTES = 10L;
    public static final String USER_PREFERENCE_TAGS_KEY = "user:preference:tags:";
    public static final Long USER_PREFERENCE_TAGS_TTL = 30L;
    public static final String RECOMMENDATION_USER_KEY = "recommend:user:";
    public static final String RECOMMENDATION_USER_VERSION_KEY = "recommend:user:";
    public static final String RECOMMENDATION_GLOBAL_VERSION_KEY = "recommend:global:version";
    public static final String RECOMMENDATION_FALLBACK_HOT_KEY = "recommend:fallback:hot:size:";
    public static final Long RECOMMENDATION_USER_TTL_MINUTES = 10L;

    public static final String LOCK_CACHE_KEY = "lock:cache:";
}
