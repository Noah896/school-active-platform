package com.campus.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.config.ActivityRegisterProperties;
import com.campus.config.ActivitySearchProperties;
import com.campus.dto.ActivityCheckInDashboardDTO;
import com.campus.dto.ActivityCheckInResultDTO;
import com.campus.dto.ActivityCheckInStatsDTO;
import com.campus.dto.ActivityCheckInSummaryDTO;
import com.campus.dto.ActivityCategoryTreeDTO;
import com.campus.dto.ActivityCheckInVerifyDTO;
import com.campus.dto.ActivityRegisterResponseDTO;
import com.campus.dto.ActivitySearchPageDTO;
import com.campus.dto.ActivitySearchSyncEventDTO;
import com.campus.dto.ActivityRegistrationEventDTO;
import com.campus.dto.ActivityRegistrationPushDTO;
import com.campus.dto.ActivityRegistrationStatusDTO;
import com.campus.dto.ActivityTrendPointDTO;
import com.campus.dto.ActivityTagOptionDTO;
import com.campus.dto.Result;
import com.campus.dto.ReviewActionDTO;
import com.campus.dto.UserPreferenceTagUpdateDTO;
import com.campus.dto.UserDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityCategory;
import com.campus.entity.ActivityCheckInRecord;
import com.campus.entity.ActivityFavorite;
import com.campus.entity.ActivityRegistration;
import com.campus.entity.ActivityTag;
import com.campus.entity.ActivityTagRelation;
import com.campus.entity.ActivityVoucher;
import com.campus.entity.User;
import com.campus.entity.UserPreferenceTag;
import com.campus.mapper.ActivityCategoryMapper;
import com.campus.mapper.ActivityCheckInRecordMapper;
import com.campus.mapper.ActivityFavoriteMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityRegistrationMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.mapper.ActivityVoucherMapper;
import com.campus.mapper.UserPreferenceTagMapper;
import com.campus.mapper.UserMapper;
import com.campus.service.ActivitySearchService;
import com.campus.service.EmbeddingTaskService;
import com.campus.service.IActivityService;
import com.campus.service.IActivityAiReviewService;
import com.campus.service.INotificationService;
import com.campus.service.IReviewRecordService;
import com.campus.utils.CacheClient;
import com.campus.utils.AuthorizationUtils;
import com.campus.utils.ActivityCategoryConstants;
import com.campus.utils.RedisIdWorker;
import com.campus.utils.RbacConstants;
import com.campus.utils.SystemConstants;
import com.campus.utils.UserHolder;
import com.campus.websocket.ActivityRegistrationSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.campus.utils.RedisConstants.ACTIVITY_CHECK_IN_IDEMPOTENCY_KEY;
import static com.campus.utils.RedisConstants.ACTIVITY_CHECK_IN_IDEMPOTENCY_TTL_MINUTES;
import static com.campus.utils.RedisConstants.ACTIVITY_FROZEN_KEY;
import static com.campus.utils.RedisConstants.ACTIVITY_META_KEY;
import static com.campus.utils.RedisConstants.ACTIVITY_REGISTER_USERS_KEY;
import static com.campus.utils.RedisConstants.ACTIVITY_STOCK_KEY;
import static com.campus.utils.RedisConstants.ACTIVITY_USER_REGISTER_STATE_KEY;
import static com.campus.utils.RedisConstants.ACTIVITY_USER_REGISTER_STATE_TTL_HOURS;
import static com.campus.utils.RedisConstants.ACTIVITY_VOUCHER_DISPLAY_KEY;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CATEGORIES_KEY;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CATEGORIES_TTL;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CHECK_IN_DASHBOARD_KEY;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CHECK_IN_DASHBOARD_TTL;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CHECK_IN_RECORDS_KEY;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CHECK_IN_RECORDS_TTL;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CHECK_IN_STATS_KEY;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_CHECK_IN_STATS_TTL;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_DETAIL_KEY;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_DETAIL_TTL;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_LIST_KEY;
import static com.campus.utils.RedisConstants.CACHE_ACTIVITY_LIST_TTL;
import static com.campus.utils.RedisConstants.RECOMMENDATION_GLOBAL_VERSION_KEY;
import static com.campus.utils.RedisConstants.RECOMMENDATION_USER_VERSION_KEY;
import static com.campus.utils.RedisConstants.USER_PREFERENCE_TAGS_KEY;
import static com.campus.utils.RedisConstants.USER_PREFERENCE_TAGS_TTL;

@Slf4j
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    private static final int STATUS_PENDING_REVIEW = 1;
    private static final int STATUS_PUBLISHED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final int STATUS_OFFLINE = 4;
    private static final int STATUS_OFFLINE_PENDING_REVIEW = 5;
    private static final String STAGE_FILTER_REGISTRATION_OPEN = "REGISTRATION_OPEN";
    private static final String STAGE_FILTER_REGISTRATION_NOT_OPEN = "REGISTRATION_NOT_OPEN";
    private static final String STAGE_FILTER_IN_PROGRESS = "IN_PROGRESS";
    private static final String STAGE_FILTER_FINISHED = "FINISHED";
    private static final List<String> ACTIVITY_CATEGORY_OPTIONS = ActivityCategoryConstants.categoryNames();
    private static final int MIN_ACTIVITY_TAG_COUNT = 1;
    private static final int MAX_ACTIVITY_TAG_COUNT = 5;
    private static final int REGISTRATION_PENDING = 0;
    private static final int REGISTRATION_SUCCESS = 1;
    private static final int REGISTRATION_FAILED = 2;
    private static final int REGISTRATION_CANCELED = 3;
    private static final int REGISTRATION_CANCEL_PENDING = 4;
    private static final String REGISTRATION_MODE_AUDIT_REQUIRED = "AUDIT_REQUIRED";
    private static final String REGISTRATION_MODE_FIRST_COME_FIRST_SERVED = "FIRST_COME_FIRST_SERVED";
    private static final int CHECKED_IN = 1;
    private static final int CHECKED_OUT = 0;
    private static final String MY_REGISTRATION_FILTER_ALL = "ALL";
    private static final String MY_REGISTRATION_FILTER_PENDING_CHECK_IN = "PENDING_CHECK_IN";
    private static final String MY_REGISTRATION_FILTER_CHECKED_IN = "CHECKED_IN";
    private static final String MY_REGISTRATION_FILTER_FINISHED = "FINISHED";
    private static final String MY_REGISTRATION_FILTER_CANCELED = "CANCELED";
    private static final String REGISTRATION_STATUS_PENDING_CONFIRM = "PENDING_CONFIRM";
    private static final String REGISTRATION_STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String REGISTRATION_STATUS_SUCCESS = "SUCCESS";
    private static final String REGISTRATION_STATUS_FAILED = "FAILED";
    private static final String REGISTRATION_STATUS_CANCELED = "CANCELED";
    private static final String REGISTRATION_STATUS_CANCEL_PENDING = "CANCEL_PENDING";
    private static final String REGISTRATION_STATUS_NONE = "NOT_REGISTERED";
    private static final int DISPLAY_CODE_LENGTH = 8;
    private static final String DISPLAY_CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String VOUCHER_STATUS_UNUSED = "UNUSED";
    private static final String VOUCHER_STATUS_CHECKED_IN = "CHECKED_IN";
    private static final String VOUCHER_STATUS_CANCELED = "CANCELED";
    private static final String VOUCHER_STATUS_EXPIRED = "EXPIRED";
    private static final String CHECK_IN_RESULT_SUCCESS = "SUCCESS";
    private static final String CHECK_IN_RESULT_ALREADY_CHECKED_IN = "ALREADY_CHECKED_IN";
    private static final String CHECK_IN_RESULT_INVALID_VOUCHER = "INVALID_VOUCHER";
    private static final String CHECK_IN_RESULT_ACTIVITY_MISMATCH = "ACTIVITY_MISMATCH";
    private static final String CHECK_IN_RESULT_OUT_OF_WINDOW = "OUT_OF_WINDOW";
    private static final String CHECK_IN_RESULT_NOT_REGISTERED = "NOT_REGISTERED";
    private static final String IDEMPOTENCY_STATUS_PROCESSING = "PROCESSING";
    private static final String IDEMPOTENCY_STATUS_SUCCESS = "SUCCESS";
    private static final String IDEMPOTENCY_STATUS_FAILED = "FAILED";
    private static final int CHECK_IN_OPEN_BEFORE_MINUTES = 30;
    private static final int CHECK_IN_CLOSE_AFTER_MINUTES = 30;
    private static final DateTimeFormatter REGISTRATION_TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter CHECK_IN_TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:00");
    private static final DefaultRedisScript<Long> ACTIVITY_REGISTER_SCRIPT;

    static {
        ACTIVITY_REGISTER_SCRIPT = new DefaultRedisScript<>();
        ACTIVITY_REGISTER_SCRIPT.setLocation(new ClassPathResource("activity_register.lua"));
        ACTIVITY_REGISTER_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ActivityRegistrationMapper activityRegistrationMapper;

    @Resource
    private ActivityVoucherMapper activityVoucherMapper;

    @Resource
    private ActivityCheckInRecordMapper activityCheckInRecordMapper;

    @Resource
    private ActivityFavoriteMapper activityFavoriteMapper;

    @Resource
    private ActivityCategoryMapper activityCategoryMapper;

    @Resource
    private ActivityTagMapper activityTagMapper;

    @Resource
    private ActivityTagRelationMapper activityTagRelationMapper;

    @Resource
    private UserPreferenceTagMapper userPreferenceTagMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private ActivityRegisterProperties activityRegisterProperties;

    @Resource
    private ActivityRegistrationSessionRegistry activityRegistrationSessionRegistry;

    @Resource
    private ActivitySearchService activitySearchService;

    @Resource
    private ActivitySearchProperties activitySearchProperties;

    @Resource
    private INotificationService notificationService;

    @Resource
    private IReviewRecordService reviewRecordService;

    @Resource
    private IActivityAiReviewService activityAiReviewService;

    @Resource
    private EmbeddingTaskService embeddingTaskService;

    @Override
    public Result queryPublicActivities(String keyword,
                                        String category,
                                        Integer status,
                                        String location,
                                        String organizerName,
                                        String stageFilter,
                                        String sortBy,
                                        LocalDateTime startTimeFrom,
                                        LocalDateTime startTimeTo,
                                        Integer current,
                                        Integer pageSize) {
        List<Activity> records;
        Long total;
        try {
            if (activitySearchService.isAvailable() && StrUtil.isBlank(stageFilter)) {
                ActivitySearchPageDTO searchPage = activitySearchService.searchActivities(
                        keyword, category, status, location, organizerName, sortBy,
                        startTimeFrom, startTimeTo, current, pageSize);
                records = searchPage.getRecords();
                total = searchPage.getTotal();
            } else {
                CachedActivityPage cachedPage = queryCachedActivityPage(keyword, category, status, location,
                        organizerName, stageFilter, sortBy, startTimeFrom, startTimeTo, current, pageSize);
                records = cachedPage.getRecords();
                total = cachedPage.getTotal();
            }
        } catch (Exception e) {
            log.warn("活动列表 ES 查询失败，降级 MySQL keyword=" + keyword
                    + ", category=" + category
                    + ", stageFilter=" + stageFilter
                    + ", sortBy=" + sortBy
                    + ", cause=" + e.getMessage());
            CachedActivityPage cachedPage = queryCachedActivityPage(keyword, category, status, location,
                    organizerName, stageFilter, sortBy, startTimeFrom, startTimeTo, current, pageSize);
            records = cachedPage.getRecords();
            total = cachedPage.getTotal();
        }
        syncRemainingSlots(records);
        enrichActivities(records, UserHolder.getUser());
        return Result.ok(records, total);
    }

    @Override
    public Result queryPublicCategories() {
        return Result.ok(queryCachedCategories());
    }

    @Override
    public Result queryMyPreferenceTags() {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(queryCachedUserPreferenceTags(userId));
    }

    @Override
    @Transactional
    public Result updateMyPreferenceTags(UserPreferenceTagUpdateDTO dto) {
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        List<Long> tagIds = dto == null ? Collections.emptyList() : normalizeTagIds(dto.getTagIds());
        if (tagIds.size() > MAX_ACTIVITY_TAG_COUNT) {
            return Result.fail("偏好标签最多选择5个");
        }
        if (!tagIds.isEmpty()) {
            Map<Long, ActivityTag> tagMap = queryTagMap(tagIds);
            if (tagMap.size() != tagIds.size()) {
                return Result.fail("偏好标签包含无效项");
            }
        }
        userPreferenceTagMapper.delete(new QueryWrapper<UserPreferenceTag>()
                .eq("user_id", userId)
                .eq("source", ActivityCategoryConstants.PREFERENCE_SOURCE_MANUAL));
        for (Long tagId : tagIds) {
            UserPreferenceTag preferenceTag = new UserPreferenceTag();
            preferenceTag.setUserId(userId);
            preferenceTag.setTagId(tagId);
            preferenceTag.setSource(ActivityCategoryConstants.PREFERENCE_SOURCE_MANUAL);
            userPreferenceTagMapper.insert(preferenceTag);
        }
        evictUserPreferenceCache(userId);
        incrementRecommendationVersion(userId);
        embeddingTaskService.touchUser(userId, "PREFERENCE_TAGS");
        return Result.ok(queryCachedUserPreferenceTags(userId));
    }

    @Override
    public Result queryActivityDetail(Long id) {
        return buildActivityDetailResult(loadActivityDetail(id, false), false);
    }

    @Override
    public Result rateLimitFallbackPublicActivities(String category, Integer current, Integer pageSize) {
        CachedActivityPage cachedPage = queryCachedActivityPage(
                null,
                category,
                null,
                null,
                null,
                null,
                "publishTimeDesc",
                null,
                null,
                current,
                pageSize
        );
        List<Activity> records = cachedPage.getRecords();
        syncRemainingSlots(records);
        enrichActivities(records, UserHolder.getUser());
        return Result.ok(records, cachedPage.getTotal());
    }

    @Override
    public Result rateLimitFallbackActivityDetail(Long id) {
        return buildActivityDetailResult(loadActivityDetail(id, true), true);
    }

    @Override
    public boolean shouldApplyRegisterRateLimit(Long activityId) {
        Activity activity = activityId == null ? null : getById(activityId);
        return activity != null && isFirstComeFirstServedMode(activity);
    }

    @Override
    @Transactional
    public Result createActivity(Activity activity) {
        UserDTO user = UserHolder.getUser();
        Result authResult = requirePermission(RbacConstants.PERM_ACTIVITY_CREATE, "无权发起活动");
        if (authResult != null) {
            return authResult;
        }
        String error = validateActivity(activity);
        if (error != null) {
            return Result.fail(error);
        }
        activity.setId(null);
        activity.setCreatorId(user.getId());
        if (StrUtil.isBlank(activity.getOrganizerName())) {
            activity.setOrganizerName(user.getNickName());
        }
        normalizeCustomCategory(activity);
        activity.setContactInfo(StrUtil.trim(activity.getContactInfo()));
        activity.setActivityFlow(StrUtil.trim(activity.getActivityFlow()));
        activity.setFaq(StrUtil.trim(activity.getFaq()));
        if (activity.getRegisteredCount() == null) {
            activity.setRegisteredCount(0);
        }
        if (activity.getStatus() == null) {
            activity.setStatus(STATUS_PENDING_REVIEW);
        }
        if (StrUtil.isBlank(activity.getRegistrationMode())) {
            activity.setRegistrationMode(REGISTRATION_MODE_AUDIT_REQUIRED);
        }
        activity.setReviewerId(null);
        activity.setReviewRemark(null);
        activity.setReviewTime(null);
        activity.setCheckInEnabled(true);
        activity.setCheckInCode(null);
        activity.setCheckInCodeExpireTime(null);
        normalizeActivityImages(activity);
        save(activity);
        replaceActivityTags(activity.getId(), activity.getTagIds());
        embeddingTaskService.touchActivity(activity.getId(), "CREATE");
        refreshActivityCacheState(activity.getId(), true);
        notifyActivitySubmitted(activity);
        activityAiReviewService.scheduleActivityReview(activity, "CREATE");
        return Result.ok(activity.getId());
    }

    @Override
    @Transactional
    public Result updateActivity(Activity activity) {
        Result authResult = requirePermission(RbacConstants.PERM_ACTIVITY_UPDATE, "无权修改活动");
        if (authResult != null) {
            return authResult;
        }
        if (activity.getId() == null) {
            return Result.fail("活动ID不能为空");
        }
        Activity existing = getById(activity.getId());
        if (existing == null) {
            return Result.fail("活动不存在");
        }
        if (!Objects.equals(existing.getCreatorId(), UserHolder.getUser().getId())) {
            return Result.fail("无权修改该活动");
        }
        String oldLocation = existing.getLocation();
        String error = validateActivity(activity);
        if (error != null) {
            return Result.fail(error);
        }
        existing.setTitle(activity.getTitle());
        existing.setCoverImage(activity.getCoverImage());
        existing.setImages(activity.getImages());
        existing.setSummary(activity.getSummary());
        existing.setContent(activity.getContent());
        existing.setActivityFlow(StrUtil.trim(activity.getActivityFlow()));
        existing.setFaq(StrUtil.trim(activity.getFaq()));
        existing.setCategory(activity.getCategory());
        existing.setCustomCategory(activity.getCustomCategory());
        existing.setRegistrationMode(resolveRegistrationMode(activity));
        existing.setContactInfo(StrUtil.trim(activity.getContactInfo()));
        existing.setLocation(activity.getLocation());
        existing.setOrganizerName(StrUtil.isBlank(activity.getOrganizerName()) ? existing.getOrganizerName() : activity.getOrganizerName());
        existing.setMaxParticipants(activity.getMaxParticipants());
        existing.setRegistrationStartTime(activity.getRegistrationStartTime());
        existing.setRegistrationEndTime(activity.getRegistrationEndTime());
        existing.setEventStartTime(activity.getEventStartTime());
        existing.setEventEndTime(activity.getEventEndTime());
        existing.setStatus(STATUS_PENDING_REVIEW);
        existing.setReviewerId(null);
        existing.setReviewRemark(null);
        existing.setReviewTime(null);
        existing.setCheckInEnabled(true);
        existing.setCheckInCode(null);
        existing.setCheckInCodeExpireTime(null);
        normalizeActivityImages(existing);
        updateById(existing);
        replaceActivityTags(existing.getId(), activity.getTagIds());
        embeddingTaskService.touchActivity(existing.getId(), "UPDATE");
        refreshActivityCacheState(existing.getId(), true);
        notifyActivitySubmitted(existing);
        activityAiReviewService.scheduleActivityReview(existing, "UPDATE");
        if (!Objects.equals(oldLocation, existing.getLocation())) {
            notifyActivityLocationChanged(existing, oldLocation);
        }
        return Result.ok();
    }

    @Override
    public Result queryMyCreatedActivities(String keyword, Integer current, Integer pageSize) {
        Result authResult = requirePermission(RbacConstants.PERM_ACTIVITY_CREATE, "无权查看我发起的活动");
        if (authResult != null) {
            return authResult;
        }
        int currentPage = current == null || current < 1 ? 1 : current;
        try {
            if (activitySearchService.isAvailable()) {
                ActivitySearchPageDTO searchPage = activitySearchService.searchActivitiesByCreator(
                        UserHolder.getUser().getId(),
                        keyword,
                        currentPage,
                        pageSize
                );
                List<Activity> records = searchPage.getRecords();
                if (records != null && (!records.isEmpty() || currentPage > 1)) {
                    syncRemainingSlots(records);
                    enrichActivities(records, UserHolder.getUser());
                    return Result.ok(records, searchPage.getTotal());
                }
            }
        } catch (Exception e) {
            log.warn("我发起的活动 ES 查询失败，降级 MySQL keyword={}", keyword, e);
        }
        QueryWrapper<Activity> wrapper = new QueryWrapper<Activity>()
                .eq("creator_id", UserHolder.getUser().getId())
                .and(StrUtil.isNotBlank(keyword), query -> applyActivityKeywordQuery(query, keyword))
                .orderByDesc("create_time");
        Page<Activity> page = page(
                new Page<>(
                        currentPage,
                        normalizePageSize(pageSize)
                ),
                wrapper
        );
        List<Activity> records = page.getRecords();
        syncRemainingSlots(records);
        enrichActivities(records, UserHolder.getUser());
        return Result.ok(records, page.getTotal());
    }

    @Override
    @Transactional
    public Result requestOfflineActivity(Long activityId, ReviewActionDTO dto) {
        Result authResult = requirePermission(RbacConstants.PERM_ACTIVITY_UPDATE, "无权申请下架活动");
        if (authResult != null) {
            return authResult;
        }
        if (activityId == null) {
            return Result.fail("活动ID不能为空");
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        if (!Objects.equals(activity.getCreatorId(), UserHolder.getUser().getId())) {
            return Result.fail("无权操作该活动");
        }
        if (!Objects.equals(activity.getStatus(), STATUS_PUBLISHED)) {
            return Result.fail("只有已发布活动可以申请下架");
        }
        if (activity.getEventStartTime() != null && !LocalDateTime.now().isBefore(activity.getEventStartTime())) {
            return Result.fail("活动已开始，不能申请下架");
        }
        activity.setStatus(STATUS_OFFLINE_PENDING_REVIEW);
        updateById(activity);
        refreshActivityCacheState(activityId, true);
        String remark = dto == null ? null : dto.getReviewRemark();
        notificationService.notifyUsers(
                Collections.singletonList(activity.getCreatorId()),
                "下架申请已提交",
                "你提交的“" + activity.getTitle() + "”下架申请已进入平台审核，请等待处理。"
                        + (StrUtil.isBlank(remark) ? "" : "原因：" + remark),
                "ACTIVITY_OFFLINE_APPLY_SUBMITTED",
                "ACTIVITY",
                activity.getId()
        );
        notificationService.notifyRole(
                RbacConstants.ROLE_PLATFORM_ADMIN,
                "有新的活动下架申请",
                "主办方申请下架活动“" + activity.getTitle() + "”，请及时审核。",
                "ACTIVITY_OFFLINE_REVIEW_PENDING",
                "ACTIVITY",
                activity.getId()
        );
        return Result.ok();
    }

    @Override
    public Result register(Long activityId) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_CREATE, "无权报名活动");
        if (authResult != null) {
            return authResult;
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        if (isAuditRequiredMode(activity)) {
            return registerAuditRequiredActivity(activity, UserHolder.getUser());//审核制的活动报名
        }
        UserDTO currentUser = UserHolder.getUser();
        ensureActivityRegistrationCache(activity);//先到先得活动进行缓存预热
        Long userId = currentUser.getId();
        String requestId = String.valueOf(redisIdWorker.nextId("activity-register"));
        Long result = stringRedisTemplate.execute(
                ACTIVITY_REGISTER_SCRIPT,
                Collections.emptyList(),
                activityId.toString(),
                userId.toString(),
                String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)),
                requestId
        );
        int code = result == null ? -1 : result.intValue();
        if (code == 0) {
            try {
                ActivityRegistrationEventDTO event = new ActivityRegistrationEventDTO();
                event.setActivityId(activityId);
                event.setUserId(userId);
                event.setRequestId(requestId);
                event.setCreateTime(LocalDateTime.now());
                rocketMQTemplate.convertAndSend(activityRegisterProperties.getTopic(), event);
                cachePendingRegistrationStatus(activityId, userId, requestId, false);
                return Result.ok(new ActivityRegisterResponseDTO(
                        activityId,
                        requestId,
                        REGISTRATION_STATUS_PENDING_CONFIRM,
                        "报名确认中，请稍候"
                ));
            } catch (Exception e) {
                log.error("活动报名消息发送失败，执行缓存补偿 activityId={}, userId={}, requestId={}",
                        activityId, userId, requestId, e);
                rollbackReservation(activityId, userId, requestId, "报名申请提交失败，请稍后重试");
                return Result.fail("报名失败，请稍后重试");
            }
        }
        if (code == 1) {
            return Result.fail("活动名额已满");
        }
        if (code == 2) {
            return Result.fail("你已经报过名了");
        }
        if (code == 3) {
            return Result.fail("活动当前不可报名");
        }
        if (code == 4) {
            return Result.fail("报名尚未开始");
        }
        if (code == 5) {
            return Result.fail("报名已经结束");
        }
        if (code == 6) {
            refreshActivityCacheState(activityId, false);
            return Result.fail("活动不存在");
        }
        return Result.fail("报名失败，请稍后重试");
    }

    @Override
    public Result queryRegistrationStatus(Long activityId) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_VIEW_SELF, "无权查看报名状态");
        if (authResult != null) {
            return authResult;
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        Long userId = UserHolder.getUser().getId();
        ActivityRegistrationStatusDTO status = resolveRegistrationStatus(activityId, userId);
        return Result.ok(status);
    }

    @Override
    @Transactional
    public Result cancelRegistration(Long activityId) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_CANCEL, "无权退出报名");
        if (authResult != null) {
            return authResult;
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        UserDTO currentUser = UserHolder.getUser();
        LocalDateTime now = LocalDateTime.now();
        if (activity.getEventStartTime() != null && !now.isBefore(activity.getEventStartTime())) {
            return Result.fail("活动已开始，无法退出报名");
        }
        if (isFirstComeFirstServedMode(activity)) {
            return cancelDirectRegistration(activity, currentUser);
        }
        ActivityRegistration registration = activityRegistrationMapper.selectOne(new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activityId)
                .eq("user_id", currentUser.getId())
                .eq("status", REGISTRATION_SUCCESS));
        if (registration == null) {
            return Result.fail("你当前未报名该活动");
        }
        ActivityVoucher voucher = activityVoucherMapper.selectOne(new QueryWrapper<ActivityVoucher>()
                .eq("registration_id", registration.getId()));
        if (voucher != null && VOUCHER_STATUS_CHECKED_IN.equals(voucher.getStatus())) {
            return Result.fail("已签到记录不可退出");
        }

        ActivityRegistration update = new ActivityRegistration();
        update.setId(registration.getId());
        update.setStatus(REGISTRATION_CANCEL_PENDING);
        update.setFailReason("退出申请待审核");
        activityRegistrationMapper.updateById(update);

        cacheCancelPendingRegistrationStatus(activityId, currentUser.getId(), registration.getRequestId(), "退出申请已提交，等待主办方审核");
        refreshActivityCacheState(activityId, false);
        notifyRegistrationCancelRequested(activity, registration);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result favoriteActivity(Long activityId) {
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        if (!isPublicActivityStatus(activity.getStatus())) {
            return Result.fail("活动尚未公开");
        }
        ActivityFavorite favorite = new ActivityFavorite();
        favorite.setActivityId(activityId);
        favorite.setUserId(UserHolder.getUser().getId());
        try {
            activityFavoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            return Result.ok();
        }
        embeddingTaskService.touchUser(UserHolder.getUser().getId(), "FAVORITE");
        return Result.ok();
    }

    @Override
    @Transactional
    public Result unfavoriteActivity(Long activityId) {
        activityFavoriteMapper.delete(new QueryWrapper<ActivityFavorite>()
                .eq("activity_id", activityId)
                .eq("user_id", UserHolder.getUser().getId()));
        embeddingTaskService.touchUser(UserHolder.getUser().getId(), "UNFAVORITE");
        return Result.ok();
    }

    @Override
    public Result queryMyFavoriteActivities(String keyword, Integer current, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        List<ActivityFavorite> favorites = activityFavoriteMapper.selectList(new QueryWrapper<ActivityFavorite>()
                .eq("user_id", userId)
                .orderByDesc("create_time"));
        if (favorites == null || favorites.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        List<Long> activityIds = favorites.stream()
                .map(ActivityFavorite::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (activityIds.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        Map<Long, Integer> orderMap = new HashMap<>(activityIds.size());
        for (int i = 0; i < activityIds.size(); i++) {
            orderMap.putIfAbsent(activityIds.get(i), i);
        }
        List<Activity> activities = listByIds(activityIds).stream()
                .filter(item -> item != null && isPublicActivityStatus(item.getStatus()))
                .sorted(Comparator.comparingInt(item -> orderMap.getOrDefault(item.getId(), Integer.MAX_VALUE)))
                .collect(Collectors.toList());
        attachActivityTags(activities);
        activities = activities.stream()
                .filter(item -> matchesFavoriteKeyword(item, keyword))
                .collect(Collectors.toList());
        enrichActivities(activities, UserHolder.getUser());
        int currentPage = current == null || current < 1 ? 1 : current;
        int normalizedPageSize = normalizePageSize(pageSize);
        int fromIndex = Math.min((currentPage - 1) * normalizedPageSize, activities.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, activities.size());
        return Result.ok(activities.subList(fromIndex, toIndex), (long) activities.size());
    }

    @Override
    public Result queryMyRegistrations(String filter, String keyword, Integer current, Integer pageSize) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_VIEW_SELF, "无权查看我的报名");
        if (authResult != null) {
            return authResult;
        }
        try {
            return queryMyRegistrationsWithSearch(filter, keyword, current, pageSize);
        } catch (Exception e) {
            log.warn("我的报名 ES 查询失败，降级 MySQL filter={}, keyword={}", filter, keyword, e);
            return queryMyRegistrationsFromMysql(filter, keyword, current, pageSize);
        }
    }

    @Override
    @Transactional
    public Result deleteMyRegistration(Long registrationId) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_VIEW_SELF, "无权删除报名记录");
        if (authResult != null) {
            return authResult;
        }
        if (registrationId == null) {
            return Result.fail("报名记录ID不能为空");
        }
        ActivityRegistration registration = activityRegistrationMapper.selectById(registrationId);
        UserDTO currentUser = UserHolder.getUser();
        if (registration == null || currentUser == null || !Objects.equals(registration.getUserId(), currentUser.getId())) {
            return Result.fail("报名记录不存在");
        }

        Activity activity = getById(registration.getActivityId());
        LocalDateTime now = LocalDateTime.now();
        boolean activityEnded = activity != null
                && activity.getEventEndTime() != null
                && !now.isBefore(activity.getEventEndTime());
        boolean canDelete = activityEnded
                || Objects.equals(registration.getStatus(), REGISTRATION_CANCELED)
                || Objects.equals(registration.getStatus(), REGISTRATION_FAILED)
                || Objects.equals(registration.getStatus(), REGISTRATION_CANCEL_PENDING);
        if (!canDelete) {
            return Result.fail("活动未结束，不能删除报名记录");
        }

        ActivityVoucher voucher = activityVoucherMapper.selectOne(new QueryWrapper<ActivityVoucher>()
                .eq("registration_id", registrationId));
        if (voucher != null) {
            activityCheckInRecordMapper.delete(new QueryWrapper<ActivityCheckInRecord>()
                    .eq("voucher_id", voucher.getId()));
            if (StrUtil.isNotBlank(voucher.getDisplayCode())) {
                stringRedisTemplate.delete(ACTIVITY_VOUCHER_DISPLAY_KEY + voucher.getDisplayCode());
            }
            activityVoucherMapper.deleteById(voucher.getId());
        }
        activityRegistrationMapper.deleteById(registrationId);
        evictCheckInCache(registration.getActivityId());
        stringRedisTemplate.delete(activityUserStateKey(registration.getActivityId(), currentUser.getId()));
        return Result.ok();
    }

    @Override
    public Result queryActivityRegistrations(Long activityId, Integer current, Integer pageSize) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_VIEW_ALL, "无权查看该活动报名名单");
        if (authResult != null) {
            return authResult;
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        if (!Objects.equals(activity.getCreatorId(), UserHolder.getUser().getId())) {
            return Result.fail("无权查看该活动报名名单");
        }
        Page<ActivityRegistration> page = new Page<>(current, normalizePageSize(pageSize));
        QueryWrapper<ActivityRegistration> wrapper = new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activityId)
                .orderByDesc("create_time");
        activityRegistrationMapper.selectPage(page, wrapper);
        List<ActivityRegistration> records = page.getRecords();
        enrichParticipantUsers(records);
        enrichRegistrationVoucherInfo(records);
        return Result.ok(records, page.getTotal());
    }

    @Override
    public Result queryMyPendingRegistrationReviews(Integer current, Integer pageSize) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_VIEW_ALL, "无权查看待审核请求");
        if (authResult != null) {
            return authResult;
        }
        Long creatorId = UserHolder.getUser().getId();
        List<Activity> myActivities = query().eq("creator_id", creatorId).list();
        if (myActivities == null || myActivities.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        Map<Long, Activity> activityMap = myActivities.stream()
                .collect(Collectors.toMap(Activity::getId, item -> item, (a, b) -> a));
        List<Long> activityIds = activityMap.values().stream()
                .filter(this::isAuditRequiredMode)
                .map(Activity::getId)
                .collect(Collectors.toList());
        if (activityIds.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        Page<ActivityRegistration> page = new Page<>(
                current == null || current < 1 ? 1 : current,
                normalizePageSize(pageSize)
        );
        QueryWrapper<ActivityRegistration> wrapper = new QueryWrapper<ActivityRegistration>()
                .in("activity_id", activityIds)
                .in("status", REGISTRATION_PENDING, REGISTRATION_CANCEL_PENDING)
                .orderByAsc("create_time");
        activityRegistrationMapper.selectPage(page, wrapper);
        List<ActivityRegistration> records = page.getRecords();
        enrichRegistrationActivitiesFromMap(records, activityMap);
        enrichParticipantUsers(records);
        enrichRegistrationVoucherInfo(records);
        return Result.ok(records, page.getTotal());
    }

    @Override
    @Transactional
    public Result reviewRegistration(Long activityId, Long registrationId, ReviewActionDTO dto) {
        Result checkResult = checkRegistrationReviewRequest(activityId, registrationId, dto);
        if (checkResult != null) {
            return checkResult;
        }
        Activity activity = getById(activityId);
        if (!Objects.equals(activity.getCreatorId(), UserHolder.getUser().getId())) {
            return Result.fail("无权审核该活动报名");
        }
        ActivityRegistration registration = activityRegistrationMapper.selectById(registrationId);
        if (registration == null || !Objects.equals(registration.getActivityId(), activityId)) {
            return Result.fail("报名记录不存在");
        }
        if (!Objects.equals(registration.getStatus(), REGISTRATION_PENDING)) {
            return Result.fail("该报名申请已处理");
        }
        if (Boolean.TRUE.equals(dto.getApproved())) {
            approveRegistration(activity, registration);
            return Result.ok();
        }
        rejectRegistration(activity, registration, dto);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result reviewCancelRegistration(Long activityId, Long registrationId, ReviewActionDTO dto) {
        Result checkResult = checkRegistrationReviewRequest(activityId, registrationId, dto);
        if (checkResult != null) {
            return checkResult;
        }
        Activity activity = getById(activityId);
        if (!Objects.equals(activity.getCreatorId(), UserHolder.getUser().getId())) {
            return Result.fail("无权审核该活动退出申请");
        }
        ActivityRegistration registration = activityRegistrationMapper.selectById(registrationId);
        if (registration == null || !Objects.equals(registration.getActivityId(), activityId)) {
            return Result.fail("报名记录不存在");
        }
        if (!Objects.equals(registration.getStatus(), REGISTRATION_CANCEL_PENDING)) {
            return Result.fail("该退出申请已处理");
        }
        if (Boolean.TRUE.equals(dto.getApproved())) {
            approveCancelRegistration(activity, registration);
            return Result.ok();
        }
        rejectCancelRegistration(activity, registration, dto);
        return Result.ok();
    }

    @Override
    public Result verifyCheckIn(Long activityId, ActivityCheckInVerifyDTO dto, String idempotencyKey) {
        Result authResult = requirePermission(RbacConstants.PERM_CHECKIN_VERIFY, "无权执行签到核销");
        if (authResult != null) {
            return authResult;
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        Long operatorId = UserHolder.getUser().getId();
        if (!Objects.equals(activity.getCreatorId(), operatorId)) {
            return Result.fail("无权执行签到核销");
        }
        if (dto == null || (dto.getVoucherId() == null && StrUtil.isBlank(dto.getDisplayCode()))) {
            return Result.fail("请提供凭证ID或展示码");
        }
        if (StrUtil.isBlank(idempotencyKey)) {
            return Result.fail("Idempotency-Key不能为空");
        }
        String fingerprint = buildCheckInFingerprint(activityId, dto);
        String redisKey = ACTIVITY_CHECK_IN_IDEMPOTENCY_KEY + activityId + ":" + idempotencyKey;
        Result cachedResult = resolveCachedCheckInResult(redisKey, fingerprint);
        if (cachedResult != null) {
            return cachedResult;
        }
        JSONObject processing = new JSONObject();
        processing.set("fingerprint", fingerprint);
        processing.set("status", IDEMPOTENCY_STATUS_PROCESSING);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                redisKey,
                processing.toString(),
                ACTIVITY_CHECK_IN_IDEMPOTENCY_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        if (!Boolean.TRUE.equals(locked)) {
            Result retryResult = resolveCachedCheckInResult(redisKey, fingerprint);
            return retryResult == null ? Result.fail("请求处理中，请稍后重试") : retryResult;
        }
        Result result;
        try {
            result = doVerifyCheckIn(activity, dto, operatorId, idempotencyKey, fingerprint);
        } catch (Exception e) {
            log.error("活动凭证核销异常 activityId={}, operatorId={}", activityId, operatorId, e);
            result = Result.fail("签到核销失败，请稍后重试");
        }
        cacheCheckInResult(redisKey, fingerprint, result);
        return result;
    }

    @Override
    public Result queryCheckInStats(Long activityId) {
        Result authResult = requirePermission(RbacConstants.PERM_CHECKIN_VIEW_RECORDS, "无权查看签到统计");
        if (authResult != null) {
            return authResult;
        }
        Result managedAuthResult = validateManagedActivityPermission(activityId, "无权查看签到统计");
        if (managedAuthResult != null) {
            return managedAuthResult;
        }
        ActivityCheckInStatsDTO stats = cacheClient.queryWithPassThrough(
                CACHE_ACTIVITY_CHECK_IN_STATS_KEY,
                activityId,
                ActivityCheckInStatsDTO.class,
                this::loadCheckInStats,
                CACHE_ACTIVITY_CHECK_IN_STATS_TTL,
                TimeUnit.MINUTES
        );
        return Result.ok(stats);
    }

    @Override
    public Result queryCheckInRecords(Long activityId, Integer current, Integer pageSize) {
        Result authResult = requirePermission(RbacConstants.PERM_CHECKIN_VIEW_RECORDS, "无权查看签到记录");
        if (authResult != null) {
            return authResult;
        }
        Result managedAuthResult = validateManagedActivityPermission(activityId, "无权查看签到记录");
        if (managedAuthResult != null) {
            return managedAuthResult;
        }
        CachedCheckInRecordPage cachedPage = queryCachedCheckInRecordPage(activityId, current, pageSize);
        return Result.ok(cachedPage.getRecords(), cachedPage.getTotal());
    }

    @Override
    public Result queryCheckInDashboard(Long activityId) {
        Result authResult = requirePermission(RbacConstants.PERM_CHECKIN_VIEW_RECORDS, "无权查看签到看板");
        if (authResult != null) {
            return authResult;
        }
        Result managedAuthResult = validateManagedActivityPermission(activityId, "无权查看签到看板");
        if (managedAuthResult != null) {
            return managedAuthResult;
        }
        ActivityCheckInDashboardDTO dashboard = cacheClient.queryWithPassThrough(
                CACHE_ACTIVITY_CHECK_IN_DASHBOARD_KEY,
                activityId,
                ActivityCheckInDashboardDTO.class,
                this::loadCheckInDashboard,
                CACHE_ACTIVITY_CHECK_IN_DASHBOARD_TTL,
                TimeUnit.MINUTES
        );
        return Result.ok(dashboard);
    }

    @Override
    public Result queryPendingReviewActivities(String keyword) {
        List<Activity> activities = query()
                .in("status", STATUS_PENDING_REVIEW, STATUS_OFFLINE_PENDING_REVIEW)
                .and(StrUtil.isNotBlank(keyword), wrapper -> applyActivityKeywordQuery(wrapper, keyword))
                .orderByDesc("create_time")
                .orderByDesc("id")
                .list();
        if (activities.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        attachActivityTags(activities);
        List<Long> creatorIds = activities.stream().map(Activity::getCreatorId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> creatorMap = creatorIds.isEmpty() ? Collections.emptyMap() : userMapper.selectBatchIds(creatorIds)
                .stream().collect(Collectors.toMap(User::getId, item -> item));
        for (Activity activity : activities) {
            User creator = creatorMap.get(activity.getCreatorId());
            if (creator != null && StrUtil.isBlank(activity.getOrganizerName())) {
                activity.setOrganizerName(creator.getNickName());
            }
        }
        return Result.ok(activities);
    }

    @Override
    public Result queryActivityAiReview(Long activityId) {
        if (activityId == null) {
            return Result.fail("活动ID不能为空");
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        return Result.ok(activityAiReviewService.queryReport(activityId));
    }

    @Override
    public Result queryPublishedActivitiesForAdmin(String keyword, Integer current, Integer pageSize) {
        if (StrUtil.isNotBlank(keyword) && activitySearchService.isAvailable()) {
            try {
                ActivitySearchPageDTO searchPage = activitySearchService.searchActivities(
                        keyword,
                        null,
                        STATUS_PUBLISHED,
                        null,
                        null,
                        "publishTimeDesc",
                        null,
                        null,
                        current,
                        pageSize
                );
                List<Activity> records = searchPage.getRecords();
                syncRemainingSlots(records);
                enrichActivities(records, UserHolder.getUser());
                return Result.ok(records, searchPage.getTotal());
            } catch (Exception e) {
                log.warn("管理员已发布活动 ES 搜索失败，降级 MySQL keyword={}", keyword, e);
            }
        }
        Page<Activity> page = query()
                .eq("status", STATUS_PUBLISHED)
                .and(StrUtil.isNotBlank(keyword), query -> applyActivityKeywordQuery(query, keyword))
                .orderByDesc("create_time")
                .page(new Page<>(
                        current == null || current < 1 ? 1 : current,
                        normalizePageSize(pageSize)
                ));
        List<Activity> records = page.getRecords();
        syncRemainingSlots(records);
        enrichActivities(records, UserHolder.getUser());
        return Result.ok(records, page.getTotal());
    }

    @Override
    @Transactional
    public Result reviewActivity(Long activityId, ReviewActionDTO dto) {
        if (activityId == null) {
            return Result.fail("活动ID不能为空");
        }
        if (dto == null || dto.getApproved() == null) {
            return Result.fail("审核结果不能为空");
        }
        if (!Boolean.TRUE.equals(dto.getApproved()) && StrUtil.isBlank(dto.getReviewRemark())) {
            return Result.fail("驳回原因不能为空");
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        boolean offlineApply = Objects.equals(activity.getStatus(), STATUS_OFFLINE_PENDING_REVIEW);
        activity.setReviewerId(UserHolder.getUser() == null ? null : UserHolder.getUser().getId());
        activity.setReviewTime(LocalDateTime.now());
        activity.setReviewRemark(Boolean.TRUE.equals(dto.getApproved()) ? null : StrUtil.trim(dto.getReviewRemark()));
        if (offlineApply) {
            activity.setStatus(Boolean.TRUE.equals(dto.getApproved()) ? STATUS_OFFLINE : STATUS_PUBLISHED);
        } else {
            activity.setStatus(Boolean.TRUE.equals(dto.getApproved()) ? STATUS_PUBLISHED : STATUS_REJECTED);
        }
        updateById(activity);
        refreshActivityCacheState(activityId, true);
        if (offlineApply) {
            notifyActivityOfflineReviewResult(activity, dto);
        } else {
            notifyActivityReviewResult(activity, dto);
        }
        reviewRecordService.record(
                RbacConstants.ROLE_PLATFORM_ADMIN,
                "PLATFORM_ADMIN",
                offlineApply ? "ACTIVITY_OFFLINE_APPLY" : "ACTIVITY",
                activity.getId(),
                activity.getTitle(),
                activity.getCreatorId(),
                activity.getOrganizerName(),
                Boolean.TRUE.equals(dto.getApproved()) ? "APPROVED" : "REJECTED",
                dto.getReviewRemark()
        );
        if (!offlineApply) {
            activityAiReviewService.recordManualReview(activity, dto.getApproved(), dto.getReviewRemark());
        }
        return Result.ok();
    }

    @Override
    @Transactional
    public Result offlineActivity(Long activityId, ReviewActionDTO dto) {
        if (activityId == null) {
            return Result.fail("活动ID不能为空");
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        if (!Objects.equals(activity.getStatus(), STATUS_PUBLISHED)) {
            return Result.fail("只有已发布活动可以下架");
        }
        activity.setStatus(STATUS_OFFLINE);
        updateById(activity);
        refreshActivityCacheState(activityId, true);
        String remark = dto == null ? null : dto.getReviewRemark();
        reviewRecordService.record(
                RbacConstants.ROLE_PLATFORM_ADMIN,
                "PLATFORM_ADMIN",
                "ACTIVITY_OFFLINE",
                activity.getId(),
                activity.getTitle(),
                activity.getCreatorId(),
                activity.getOrganizerName(),
                "OFFLINE",
                remark
        );
        notificationService.notifyUsers(
                Collections.singletonList(activity.getCreatorId()),
                "活动已被下架",
                "你发布的“" + activity.getTitle() + "”已被平台管理员下架。"
                        + (StrUtil.isBlank(remark) ? "" : "原因：" + remark),
                "ACTIVITY_OFFLINE",
                "ACTIVITY",
                activity.getId()
        );
        return Result.ok();
    }

    private Result doVerifyCheckIn(Activity activity, ActivityCheckInVerifyDTO dto, Long operatorId,
                                   String idempotencyKey, String fingerprint) {
        ActivityVoucher voucher = findVoucher(dto);
        if (voucher == null) {
            Result result = Result.fail("凭证不存在或无效");
            recordCheckInAttempt(activity.getId(), null, null, operatorId, idempotencyKey, fingerprint,
                    CHECK_IN_RESULT_INVALID_VOUCHER, result);
            return result;
        }
        if (!Objects.equals(voucher.getActivityId(), activity.getId())) {
            Result result = Result.fail("凭证与当前活动不匹配");
            recordCheckInAttempt(activity.getId(), voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey,
                    fingerprint, CHECK_IN_RESULT_ACTIVITY_MISMATCH, result);
            return result;
        }
        ActivityRegistration registration = activityRegistrationMapper.selectById(voucher.getRegistrationId());
        if (registration == null || !Objects.equals(registration.getStatus(), REGISTRATION_SUCCESS)) {
            Result result = Result.fail("报名记录不存在或已失效");
            recordCheckInAttempt(activity.getId(), voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey,
                    fingerprint, CHECK_IN_RESULT_NOT_REGISTERED, result);
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        if (isBeforeCheckInWindow(activity, now)) {
            Result result = Result.fail("未到签到时间窗口");
            recordCheckInAttempt(activity.getId(), voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey,
                    fingerprint, CHECK_IN_RESULT_OUT_OF_WINDOW, result);
            return result;
        }
        if (isAfterCheckInWindow(activity, now)) {
            expireVoucherIfNeeded(voucher);
            Result result = Result.fail("已超过签到时间窗口");
            recordCheckInAttempt(activity.getId(), voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey,
                    fingerprint, CHECK_IN_RESULT_OUT_OF_WINDOW, result);
            return result;
        }
        if (!VOUCHER_STATUS_UNUSED.equals(voucher.getStatus())) {
            return handleUnavailableVoucher(activity.getId(), voucher, operatorId, idempotencyKey, fingerprint);
        }
        return completeCheckIn(activity.getId(), registration, voucher, operatorId, idempotencyKey, fingerprint, now);
    }

    @Transactional
    public Result completeCheckIn(Long activityId, ActivityRegistration registration, ActivityVoucher voucher,
                                  Long operatorId, String idempotencyKey, String fingerprint, LocalDateTime now) {
        UpdateWrapper<ActivityVoucher> voucherUpdate = new UpdateWrapper<>();
        voucherUpdate.eq("id", voucher.getId())
                .eq("status", VOUCHER_STATUS_UNUSED)
                .set("status", VOUCHER_STATUS_CHECKED_IN)
                .set("checked_in_time", now)
                .set("checked_in_by", operatorId);
        int updated = activityVoucherMapper.update(null, voucherUpdate);
        if (updated == 0) {
            ActivityVoucher latest = activityVoucherMapper.selectById(voucher.getId());
            if (latest == null) {
                Result result = Result.fail("凭证不存在或无效");
                recordCheckInAttempt(activityId, voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey,
                        fingerprint, CHECK_IN_RESULT_INVALID_VOUCHER, result);
                return result;
            }
            return handleUnavailableVoucher(activityId, latest, operatorId, idempotencyKey, fingerprint);
        }

        UpdateWrapper<ActivityRegistration> registrationUpdate = new UpdateWrapper<>();
        registrationUpdate.eq("id", registration.getId())
                .set("check_in_status", CHECKED_IN)
                .set("check_in_time", now)
                .set("voucher_id", voucher.getId());
        activityRegistrationMapper.update(null, registrationUpdate);

        ActivityVoucher latest = activityVoucherMapper.selectById(voucher.getId());
        ActivityCheckInResultDTO payload = buildCheckInResult(activityId, latest, CHECK_IN_RESULT_SUCCESS, "签到成功");
        Result result = Result.ok(payload);
        evictCheckInCache(activityId);
        recordCheckInAttempt(activityId, voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey, fingerprint,
                CHECK_IN_RESULT_SUCCESS, result);
        embeddingTaskService.touchUser(voucher.getUserId(), "CHECK_IN");
        return result;
    }

    private Result handleUnavailableVoucher(Long activityId, ActivityVoucher voucher, Long operatorId,
                                            String idempotencyKey, String fingerprint) {
        if (VOUCHER_STATUS_CHECKED_IN.equals(voucher.getStatus())) {
            ActivityCheckInResultDTO payload = buildCheckInResult(activityId, voucher,
                    CHECK_IN_RESULT_ALREADY_CHECKED_IN, "该凭证已签到");
            Result result = Result.ok(payload);
            recordCheckInAttempt(activityId, voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey, fingerprint,
                    CHECK_IN_RESULT_ALREADY_CHECKED_IN, result);
            return result;
        }
        String message = VOUCHER_STATUS_CANCELED.equals(voucher.getStatus()) ? "凭证已取消" :
                (VOUCHER_STATUS_EXPIRED.equals(voucher.getStatus()) ? "凭证已失效" : "凭证当前不可签到");
        Result result = Result.fail(message);
        recordCheckInAttempt(activityId, voucher.getId(), voucher.getUserId(), operatorId, idempotencyKey, fingerprint,
                CHECK_IN_RESULT_INVALID_VOUCHER, result);
        return result;
    }

    private Result resolveCachedCheckInResult(String redisKey, String fingerprint) {
        String cachedJson = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isBlank(cachedJson)) {
            return null;
        }
        JSONObject jsonObject = JSONUtil.parseObj(cachedJson);
        String cachedFingerprint = jsonObject.getStr("fingerprint");
        if (StrUtil.isNotBlank(cachedFingerprint) && !Objects.equals(cachedFingerprint, fingerprint)) {
            return Result.fail("Idempotency-Key与请求参数冲突");
        }
        String status = jsonObject.getStr("status");
        if (IDEMPOTENCY_STATUS_PROCESSING.equals(status)) {
            return Result.fail("请求处理中，请稍后重试");
        }
        Boolean success = jsonObject.getBool("success");
        if (Boolean.TRUE.equals(success)) {
            Object response = jsonObject.get("data");
            ActivityCheckInResultDTO payload = response == null ? null :
                    JSONUtil.toBean(JSONUtil.parseObj(response), ActivityCheckInResultDTO.class);
            return Result.ok(payload);
        }
        return Result.fail(jsonObject.getStr("errorMsg", "签到核销失败，请稍后重试"));
    }

    private void cacheCheckInResult(String redisKey, String fingerprint, Result result) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.set("fingerprint", fingerprint);
        jsonObject.set("status", Boolean.TRUE.equals(result.getSuccess()) ? IDEMPOTENCY_STATUS_SUCCESS : IDEMPOTENCY_STATUS_FAILED);
        jsonObject.set("success", result.getSuccess());
        jsonObject.set("errorMsg", result.getErrorMsg());
        jsonObject.set("data", result.getData());
        stringRedisTemplate.opsForValue().set(redisKey, jsonObject.toString(),
                ACTIVITY_CHECK_IN_IDEMPOTENCY_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private ActivityCheckInResultDTO buildCheckInResult(Long activityId, ActivityVoucher voucher,
                                                        String resultStatus, String message) {
        ActivityCheckInResultDTO payload = new ActivityCheckInResultDTO();
        payload.setActivityId(activityId);
        payload.setVoucherId(voucher.getId());
        payload.setDisplayCode(voucher.getDisplayCode());
        payload.setUserId(voucher.getUserId());
        payload.setUserNickName(voucher.getUserNickName());
        payload.setVoucherStatus(voucher.getStatus());
        payload.setResultStatus(resultStatus);
        payload.setMessage(message);
        payload.setCheckedInTime(voucher.getCheckedInTime());
        payload.setCheckedInBy(voucher.getCheckedInBy());
        return payload;
    }

    private void recordCheckInAttempt(Long activityId, Long voucherId, Long userId, Long operatorId, String requestKey,
                                      String requestFingerprint, String resultStatus, Result result) {
        ActivityCheckInRecord record = new ActivityCheckInRecord();
        record.setId(redisIdWorker.nextId("activity-checkin-record"));
        record.setActivityId(activityId);
        record.setVoucherId(voucherId);
        record.setUserId(userId);
        record.setOperatorId(operatorId);
        record.setResultStatus(resultStatus);
        record.setRequestKey(requestKey);
        record.setRequestFingerprint(requestFingerprint);
        record.setResponseBody(JSONUtil.toJsonStr(result));
        activityCheckInRecordMapper.insert(record);
    }

    private ActivityVoucher findVoucher(ActivityCheckInVerifyDTO dto) {
        if (dto.getVoucherId() != null) {
            ActivityVoucher voucher = activityVoucherMapper.selectById(dto.getVoucherId());
            if (voucher != null && StrUtil.isNotBlank(voucher.getDisplayCode())) {
                cacheVoucherDisplayCode(voucher);
            }
            return voucher;
        }
        String displayCode = dto.getDisplayCode().trim().toUpperCase();
        String cachedVoucherId = stringRedisTemplate.opsForValue().get(ACTIVITY_VOUCHER_DISPLAY_KEY + displayCode);
        ActivityVoucher voucher = null;
        if (StrUtil.isNotBlank(cachedVoucherId)) {
            voucher = activityVoucherMapper.selectById(Long.valueOf(cachedVoucherId));
        }
        if (voucher == null) {
            voucher = activityVoucherMapper.selectOne(new QueryWrapper<ActivityVoucher>().eq("display_code", displayCode));
            if (voucher != null) {
                cacheVoucherDisplayCode(voucher);
            }
        }
        if (voucher != null) {
            attachVoucherUserInfo(Collections.singletonList(voucher));
        }
        return voucher;
    }

    private void cacheVoucherDisplayCode(ActivityVoucher voucher) {
        if (voucher == null || StrUtil.isBlank(voucher.getDisplayCode())) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                ACTIVITY_VOUCHER_DISPLAY_KEY + voucher.getDisplayCode(),
                voucher.getId().toString(),
                1,
                TimeUnit.DAYS
        );
    }

    private String buildCheckInFingerprint(Long activityId, ActivityCheckInVerifyDTO dto) {
        String payload = "activityId=" + activityId
                + "|voucherId=" + String.valueOf(dto.getVoucherId())
                + "|displayCode=" + StrUtil.blankToDefault(dto.getDisplayCode(), "").trim().toUpperCase();
        return DigestUtil.md5Hex(payload);
    }

    private boolean isBeforeCheckInWindow(Activity activity, LocalDateTime now) {
        return activity.getEventStartTime() != null
                && now.isBefore(activity.getEventStartTime().minusMinutes(CHECK_IN_OPEN_BEFORE_MINUTES));
    }

    private boolean isAfterCheckInWindow(Activity activity, LocalDateTime now) {
        return activity.getEventEndTime() != null
                && now.isAfter(activity.getEventEndTime().plusMinutes(CHECK_IN_CLOSE_AFTER_MINUTES));
    }

    private void expireVoucherIfNeeded(ActivityVoucher voucher) {
        if (voucher == null || !VOUCHER_STATUS_UNUSED.equals(voucher.getStatus())) {
            return;
        }
        UpdateWrapper<ActivityVoucher> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", voucher.getId())
                .eq("status", VOUCHER_STATUS_UNUSED)
                .set("status", VOUCHER_STATUS_EXPIRED);
        activityVoucherMapper.update(null, wrapper);
        voucher.setStatus(VOUCHER_STATUS_EXPIRED);
    }

    private void enrichActivities(List<Activity> activities, UserDTO user) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        attachActivityTags(activities);
        Map<Long, ActivityUserStateCache> userStateMap = Collections.emptyMap();
        Set<Long> favoriteIds = Collections.emptySet();
        if (user != null) {
            List<Long> activityIds = activities.stream().map(Activity::getId).collect(Collectors.toList());
            userStateMap = queryActivityUserStateCache(activityIds, user.getId());
            favoriteIds = queryActivityFavoriteIds(activityIds, user.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        for (Activity activity : activities) {
            ActivityUserStateCache userState = userStateMap.get(activity.getId());
            boolean registered = userState != null
                    && (REGISTRATION_STATUS_SUCCESS.equals(userState.getStatus())
                    || REGISTRATION_STATUS_CANCEL_PENDING.equals(userState.getStatus()));
            if (!registered && user != null) {
                Boolean member = stringRedisTemplate.opsForSet().isMember(activityRegisterUsersKey(activity.getId()), user.getId().toString());
                registered = Boolean.TRUE.equals(member) && userState != null
                        && (REGISTRATION_STATUS_SUCCESS.equals(userState.getStatus())
                        || REGISTRATION_STATUS_CANCEL_PENDING.equals(userState.getStatus()));
            }
            activity.setRegistered(registered);
            activity.setCheckedIn(userState != null && Boolean.TRUE.equals(userState.getCheckedIn()));
            boolean canManage = user != null
                    && AuthorizationUtils.hasPermission(user, RbacConstants.PERM_ACTIVITY_UPDATE)
                    && Objects.equals(activity.getCreatorId(), user.getId());
            activity.setCanManage(canManage);
            activity.setRegistrationOpen(isRegistrationOpen(activity, now));
            activity.setRegistrationStatus(userState == null ? REGISTRATION_STATUS_NONE : userState.getStatus());
            activity.setRegistrationMessage(userState == null ? "未报名" : userState.getMessage());
            activity.setRegistrationRequestId(userState == null ? null : userState.getRequestId());
            activity.setRegistrationFailReason(userState == null ? null : userState.getFailReason());
            activity.setVoucherId(userState == null ? null : userState.getVoucherId());
            activity.setVoucherDisplayCode(userState == null ? null : userState.getVoucherDisplayCode());
            activity.setVoucherStatus(userState == null ? null : userState.getVoucherStatus());
            activity.setVoucherIssuedTime(userState == null ? null : userState.getVoucherIssuedTime());
            activity.setVoucherCheckedInTime(userState == null ? null : userState.getVoucherCheckedInTime());
            activity.setFavorited(favoriteIds.contains(activity.getId()));
            activity.setCheckInCode(null);
            activity.setCheckInCodeExpireTime(null);
        }
    }

    private Set<Long> queryActivityFavoriteIds(List<Long> activityIds, Long userId) {
        if (activityIds == null || activityIds.isEmpty() || userId == null) {
            return Collections.emptySet();
        }
        return activityFavoriteMapper.selectList(new QueryWrapper<ActivityFavorite>()
                        .select("activity_id")
                        .eq("user_id", userId)
                        .in("activity_id", activityIds))
                .stream()
                .map(ActivityFavorite::getActivityId)
                .collect(Collectors.toSet());
    }

    private void enrichRegistrationActivities(List<ActivityRegistration> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        List<Long> activityIds = registrations.stream().map(ActivityRegistration::getActivityId).distinct().collect(Collectors.toList());
        Map<Long, Activity> activityMap = listByIds(activityIds).stream().collect(Collectors.toMap(Activity::getId, a -> a));
        attachActivityTags(new ArrayList<>(activityMap.values()));
        enrichRegistrationActivitiesFromMap(registrations, activityMap);
    }

    private void enrichRegistrationActivitiesFromMap(List<ActivityRegistration> registrations, Map<Long, Activity> activityMap) {
        if (registrations == null || registrations.isEmpty() || activityMap == null || activityMap.isEmpty()) {
            return;
        }
        for (ActivityRegistration registration : registrations) {
            Activity activity = activityMap.get(registration.getActivityId());
            if (activity == null) {
                continue;
            }
            registration.setActivityTitle(activity.getTitle());
            registration.setActivityCoverImage(activity.getCoverImage());
            registration.setCoverImage(activity.getCoverImage());
            registration.setCategory(StrUtil.blankToDefault(activity.getDisplayCategory(), activity.getCategory()));
            registration.setRegistrationMode(resolveRegistrationMode(activity));
            registration.setLocation(activity.getLocation());
            registration.setOrganizerName(activity.getOrganizerName());
            registration.setEventStartTime(activity.getEventStartTime());
            registration.setEventEndTime(activity.getEventEndTime());
            registration.setCheckInEnabled(Boolean.TRUE);
            registration.setStatusText(readRegistrationStatusText(activity, registration.getStatus()));
        }
    }

    private void enrichParticipantUsers(List<ActivityRegistration> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        List<Long> userIds = registrations.stream().map(ActivityRegistration::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        for (ActivityRegistration registration : registrations) {
            User user = userMap.get(registration.getUserId());
            if (user == null) {
                continue;
            }
            registration.setUserNickName(user.getNickName());
            registration.setUserPhone(user.getPhone());
            registration.setUserIcon(user.getIcon());
        }
    }

    private void enrichRegistrationVoucherInfo(List<ActivityRegistration> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        List<Long> registrationIds = registrations.stream().map(ActivityRegistration::getId).collect(Collectors.toList());
        List<ActivityVoucher> vouchers = activityVoucherMapper.selectList(new QueryWrapper<ActivityVoucher>()
                .in("registration_id", registrationIds));
        Map<Long, ActivityVoucher> voucherMap = vouchers.stream()
                .collect(Collectors.toMap(ActivityVoucher::getRegistrationId, v -> v, (a, b) -> a));
        Map<Long, String> operatorNameMap = queryUserNameMap(vouchers.stream()
                .map(ActivityVoucher::getCheckedInBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        for (ActivityRegistration registration : registrations) {
            ActivityVoucher voucher = voucherMap.get(registration.getId());
            if (voucher == null) {
                continue;
            }
            registration.setVoucherId(voucher.getId());
            registration.setVoucherDisplayCode(voucher.getDisplayCode());
            registration.setVoucherStatus(voucher.getStatus());
            registration.setVoucherIssuedTime(voucher.getIssuedTime());
            registration.setCheckedInBy(voucher.getCheckedInBy());
            registration.setCheckedInByName(operatorNameMap.get(voucher.getCheckedInBy()));
            registration.setCheckInStatus(VOUCHER_STATUS_CHECKED_IN.equals(voucher.getStatus()) ? CHECKED_IN : CHECKED_OUT);
            registration.setCheckInTime(voucher.getCheckedInTime());
        }
    }

    private void enrichCheckInRecords(List<ActivityCheckInRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> userIds = new ArrayList<>();
        for (ActivityCheckInRecord record : records) {
            if (record.getUserId() != null) {
                userIds.add(record.getUserId());
            }
            if (record.getOperatorId() != null) {
                userIds.add(record.getOperatorId());
            }
        }
        Map<Long, String> userNameMap = queryUserNameMap(userIds.stream().distinct().collect(Collectors.toList()));
        for (ActivityCheckInRecord record : records) {
            if (record.getResponseBody() == null) {
                continue;
            }
            JSONObject response = JSONUtil.parseObj(record.getResponseBody());
            JSONObject data = response.getJSONObject("data");
            if (data != null) {
                data.set("userNickName", userNameMap.get(record.getUserId()));
                data.set("operatorName", userNameMap.get(record.getOperatorId()));
                record.setResponseBody(data.toString());
            }
        }
    }

    private Map<Long, ActivityVoucher> queryVoucherMapByActivityIdsAndUserId(List<Long> activityIds, Long userId) {
        if (activityIds == null || activityIds.isEmpty() || userId == null) {
            return Collections.emptyMap();
        }
        List<ActivityVoucher> vouchers = activityVoucherMapper.selectList(new QueryWrapper<ActivityVoucher>()
                .eq("user_id", userId)
                .in("activity_id", activityIds));
        return vouchers.stream().collect(Collectors.toMap(ActivityVoucher::getActivityId, v -> v, (a, b) -> a));
    }

    private Map<Long, ActivityUserStateCache> queryActivityUserStateCache(List<Long> activityIds, Long userId) {
        if (activityIds == null || activityIds.isEmpty() || userId == null) {
            return Collections.emptyMap();
        }
        Map<Long, ActivityUserStateCache> stateMap = new HashMap<>(activityIds.size());
        List<Long> missedActivityIds = new ArrayList<>();
        for (Long activityId : activityIds) {
            ActivityUserStateCache redisState = getUserStateFromRedis(activityId, userId);
            if (redisState == null) {
                missedActivityIds.add(activityId);
                continue;
            }
            stateMap.put(activityId, redisState);
        }
        if (missedActivityIds.isEmpty()) {
            return stateMap;
        }

        List<ActivityRegistration> registrations = activityRegistrationMapper.selectList(new QueryWrapper<ActivityRegistration>()
                .eq("user_id", userId)
                .in("activity_id", missedActivityIds));
        Map<Long, Activity> activityMap = listByIds(missedActivityIds).stream()
                .collect(Collectors.toMap(Activity::getId, item -> item, (a, b) -> a));
        Map<Long, ActivityRegistration> registrationMap = registrations.stream()
                .collect(Collectors.toMap(ActivityRegistration::getActivityId, r -> r, (a, b) -> a));
        Map<Long, ActivityVoucher> voucherMap = queryVoucherMapByActivityIdsAndUserId(missedActivityIds, userId);

        for (Long activityId : missedActivityIds) {
            ActivityRegistration registration = registrationMap.get(activityId);
            ActivityVoucher voucher = voucherMap.get(activityId);
            ActivityUserStateCache state = buildUserState(registration, voucher, activityMap.get(activityId));
            stateMap.put(activityId, state);
            cacheUserRegistrationState(activityId, userId, state);
        }
        return stateMap;
    }

    private ActivityCheckInStatsDTO loadCheckInStats(Long activityId) {
        long registeredCount = activityRegistrationMapper.selectCount(new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activityId)
                .eq("status", REGISTRATION_SUCCESS));
        long checkedInCount = activityVoucherMapper.selectCount(new QueryWrapper<ActivityVoucher>()
                .eq("activity_id", activityId)
                .eq("status", VOUCHER_STATUS_CHECKED_IN));
        long uncheckedCount = Math.max(registeredCount - checkedInCount, 0);
        double checkInRate = registeredCount == 0 ? 0D : (checkedInCount * 100D / registeredCount);
        return new ActivityCheckInStatsDTO(registeredCount, checkedInCount, uncheckedCount, checkInRate);
    }

    private ActivityCheckInDashboardDTO loadCheckInDashboard(Long activityId) {
        Activity activity = loadActivityDetail(activityId, false);
        if (activity == null) {
            return null;
        }
        ActivityCheckInDashboardDTO dashboard = new ActivityCheckInDashboardDTO();
        dashboard.setActivitySummary(buildCheckInSummary(activity));
        ActivityCheckInStatsDTO stats = loadCheckInStats(activityId);
        dashboard.setStats(stats);
        dashboard.setStatusChart(buildCheckInStatusChart(stats));
        dashboard.setRegistrationTrendChart(buildRegistrationTrendChart(activityId));
        dashboard.setCheckInTrendChart(buildCheckInTrendChart(activityId));
        dashboard.setRecentRecords(queryCachedCheckInRecordPage(activityId, 1, 10).getRecords());
        return dashboard;
    }

    private ActivityCheckInSummaryDTO buildCheckInSummary(Activity activity) {
        ActivityCheckInSummaryDTO summary = new ActivityCheckInSummaryDTO();
        summary.setActivityId(activity.getId());
        summary.setTitle(activity.getTitle());
        summary.setOrganizerName(activity.getOrganizerName());
        summary.setLocation(activity.getLocation());
        summary.setStatus(activity.getStatus());
        summary.setRegisteredCount(activity.getRegisteredCount());
        summary.setMaxParticipants(activity.getMaxParticipants());
        summary.setRegistrationStartTime(activity.getRegistrationStartTime());
        summary.setRegistrationEndTime(activity.getRegistrationEndTime());
        summary.setEventStartTime(activity.getEventStartTime());
        summary.setEventEndTime(activity.getEventEndTime());
        return summary;
    }

    private List<ActivityTrendPointDTO> buildCheckInStatusChart(ActivityCheckInStatsDTO stats) {
        List<ActivityTrendPointDTO> chart = new ArrayList<>(2);
        chart.add(new ActivityTrendPointDTO("已签到", stats == null ? 0L : defaultLong(stats.getCheckedInCount())));
        chart.add(new ActivityTrendPointDTO("未签到", stats == null ? 0L : defaultLong(stats.getUncheckedCount())));
        return chart;
    }

    private List<ActivityTrendPointDTO> buildRegistrationTrendChart(Long activityId) {
        List<ActivityRegistration> registrations = activityRegistrationMapper.selectList(new QueryWrapper<ActivityRegistration>()
                .select("create_time")
                .eq("activity_id", activityId)
                .eq("status", REGISTRATION_SUCCESS)
                .orderByAsc("create_time"));
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (ActivityRegistration registration : registrations) {
            if (registration.getCreateTime() == null) {
                continue;
            }
            String label = registration.getCreateTime().format(REGISTRATION_TREND_LABEL_FORMATTER);
            grouped.merge(label, 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(entry -> new ActivityTrendPointDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private List<ActivityTrendPointDTO> buildCheckInTrendChart(Long activityId) {
        List<ActivityCheckInRecord> records = activityCheckInRecordMapper.selectList(new QueryWrapper<ActivityCheckInRecord>()
                .select("create_time")
                .eq("activity_id", activityId)
                .eq("result_status", CHECK_IN_RESULT_SUCCESS)
                .orderByAsc("create_time"));
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (ActivityCheckInRecord record : records) {
            if (record.getCreateTime() == null) {
                continue;
            }
            String label = record.getCreateTime().withMinute(0).withSecond(0).withNano(0)
                    .format(CHECK_IN_TREND_LABEL_FORMATTER);
            grouped.merge(label, 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(entry -> new ActivityTrendPointDTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private CachedCheckInRecordPage queryCachedCheckInRecordPage(Long activityId, Integer current, Integer pageSize) {
        int normalizedPageSize = normalizePageSize(pageSize);
        int currentPage = current == null || current < 1 ? 1 : current;
        String key = activityCheckInRecordsKey(activityId, currentPage, normalizedPageSize);
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cacheJson)) {
            JSONObject jsonObject = JSONUtil.parseObj(cacheJson);
            JSONArray recordsArray = jsonObject.getJSONArray("records");
            List<ActivityCheckInRecord> records = recordsArray == null ? new ArrayList<>() : recordsArray.toList(ActivityCheckInRecord.class);
            Long total = jsonObject.getLong("total", 0L);
            return new CachedCheckInRecordPage(records, total);
        }
        Page<ActivityCheckInRecord> page = new Page<>(currentPage, normalizedPageSize);
        QueryWrapper<ActivityCheckInRecord> wrapper = new QueryWrapper<ActivityCheckInRecord>()
                .eq("activity_id", activityId)
                .eq("result_status", CHECK_IN_RESULT_SUCCESS)
                .orderByDesc("create_time");
        activityCheckInRecordMapper.selectPage(page, wrapper);
        List<ActivityCheckInRecord> records = page.getRecords();
        enrichCheckInRecords(records);
        Map<String, Object> cacheValue = new HashMap<>(2);
        cacheValue.put("total", page.getTotal());
        cacheValue.put("records", records);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(cacheValue), CACHE_ACTIVITY_CHECK_IN_RECORDS_TTL, TimeUnit.MINUTES);
        return new CachedCheckInRecordPage(records, page.getTotal());
    }

    private Map<Long, String> queryUserNameMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickName, (a, b) -> a));
    }

    private Result checkRegistrationReviewRequest(Long activityId, Long registrationId, ReviewActionDTO dto) {
        Result authResult = requirePermission(RbacConstants.PERM_REGISTRATION_VIEW_ALL, "无权审核报名申请");
        if (authResult != null) {
            return authResult;
        }
        if (activityId == null) {
            return Result.fail("活动ID不能为空");
        }
        if (registrationId == null) {
            return Result.fail("报名记录ID不能为空");
        }
        if (dto == null || dto.getApproved() == null) {
            return Result.fail("审核结果不能为空");
        }
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        return null;
    }

        private Result registerAuditRequiredActivity(Activity activity, UserDTO currentUser) {
            if (currentUser == null || currentUser.getId() == null) {
                return Result.fail("请先登录");
            }
            LocalDateTime now = LocalDateTime.now();
            if (!isPublicActivityStatus(activity.getStatus())) {
                return Result.fail("活动当前不可报名");
            }
            if (activity.getRegistrationStartTime() != null && now.isBefore(activity.getRegistrationStartTime())) {
                return Result.fail("报名尚未开始");
            }
            if (activity.getRegistrationEndTime() != null && now.isAfter(activity.getRegistrationEndTime())) {
                return Result.fail("报名已经结束");
            }
            ActivityRegistration existing = activityRegistrationMapper.selectOne(new QueryWrapper<ActivityRegistration>()
                    .eq("activity_id", activity.getId())
                    .eq("user_id", currentUser.getId()));
            if (existing != null) {
                if (Objects.equals(existing.getStatus(), REGISTRATION_SUCCESS)) {
                    return Result.fail("你已经报过名了");
                }
                if (Objects.equals(existing.getStatus(), REGISTRATION_PENDING)) {
                    ActivityRegistrationStatusDTO status = buildRegistrationStatus(existing, null, activity);
                    cacheFinalRegistrationStatus(activity.getId(), currentUser.getId(), status);
                    return Result.ok(status);
                }
                if (Objects.equals(existing.getStatus(), REGISTRATION_CANCEL_PENDING)) {
                    return Result.fail("你有一条退出申请待审核，暂不能重新报名");
                }
            }
            String requestId = String.valueOf(redisIdWorker.nextId("activity-register"));
            ActivityRegistration registration = existing == null ? new ActivityRegistration() : existing;
            registration.setActivityId(activity.getId());
            registration.setUserId(currentUser.getId());
            registration.setStatus(REGISTRATION_PENDING);
            registration.setRequestId(requestId);
            registration.setFailReason(null);
            registration.setVoucherId(null);
            registration.setCheckInStatus(CHECKED_OUT);
            registration.setCheckInTime(null);
            registration.setConfirmTime(LocalDateTime.now());
            if (existing == null) {
                activityRegistrationMapper.insert(registration);
            } else {
                activityRegistrationMapper.updateById(registration);
            }
            ActivityRegistrationStatusDTO status = buildRegistrationStatus(registration, null, activity);
            cacheFinalRegistrationStatus(activity.getId(), currentUser.getId(), status);
            refreshActivityCacheState(activity.getId(), false);
            embeddingTaskService.touchUser(currentUser.getId(), "REGISTRATION_REQUEST");
            notifyRegistrationRequested(activity, currentUser.getId());
            return Result.ok(new ActivityRegisterResponseDTO(
                    activity.getId(),
                    requestId,
                    REGISTRATION_STATUS_PENDING_REVIEW,
                    status.getMessage()
            ));
        }

    @Transactional
    public Result cancelDirectRegistration(Activity activity, UserDTO currentUser) {
        ActivityRegistration registration = activityRegistrationMapper.selectOne(new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activity.getId())
                .eq("user_id", currentUser.getId())
                .eq("status", REGISTRATION_SUCCESS));
        if (registration == null) {
            return Result.fail("你当前未报名该活动");
        }
        ActivityVoucher voucher = activityVoucherMapper.selectOne(new QueryWrapper<ActivityVoucher>()
                .eq("registration_id", registration.getId()));
        if (voucher != null && VOUCHER_STATUS_CHECKED_IN.equals(voucher.getStatus())) {
            return Result.fail("已签到记录不可退出");
        }
        if (voucher != null) {
            activityVoucherMapper.deleteById(voucher.getId());
            if (StrUtil.isNotBlank(voucher.getDisplayCode())) {
                stringRedisTemplate.delete(ACTIVITY_VOUCHER_DISPLAY_KEY + voucher.getDisplayCode());
            }
        }
        ActivityRegistration update = new ActivityRegistration();
        update.setId(registration.getId());
        update.setStatus(REGISTRATION_CANCELED);
        update.setFailReason("已退出活动");
        update.setVoucherId(null);
        update.setConfirmTime(LocalDateTime.now());
        activityRegistrationMapper.updateById(update);
        UpdateWrapper<Activity> wrapper = new UpdateWrapper<>();
        wrapper.setSql("registered_count = registered_count - 1")
                .eq("id", activity.getId())
                .gt("registered_count", 0);
        update(null, wrapper);
        stringRedisTemplate.opsForSet().remove(activityRegisterUsersKey(activity.getId()), currentUser.getId().toString());
        stringRedisTemplate.opsForValue().increment(activityStockKey(activity.getId()));
        cacheCanceledRegistrationStatus(activity.getId(), currentUser.getId(), registration.getRequestId(), "已退出活动");
        refreshActivityCacheState(activity.getId(), false);
        embeddingTaskService.touchUser(currentUser.getId(), "REGISTRATION_CANCEL");
        incrementRecommendationVersion(currentUser.getId());
        return Result.ok();
    }

    private void approveRegistration(Activity activity, ActivityRegistration registration) {
        ActivityRegistration update = new ActivityRegistration();
        update.setId(registration.getId());
        update.setStatus(REGISTRATION_SUCCESS);
        update.setFailReason(null);
        update.setCheckInStatus(CHECKED_OUT);
        update.setConfirmTime(LocalDateTime.now());
        activityRegistrationMapper.updateById(update);
        registration.setStatus(REGISTRATION_SUCCESS);
        registration.setFailReason(null);
        registration.setCheckInStatus(CHECKED_OUT);
        registration.setConfirmTime(update.getConfirmTime());

        UpdateWrapper<Activity> wrapper = new UpdateWrapper<>();
        wrapper.setSql("registered_count = registered_count + 1")
                .eq("id", activity.getId())
                .lt("registered_count", activity.getMaxParticipants());
        boolean updated = update(null, wrapper);
        if (!updated) {
            throw new IllegalStateException("报名审核通过时名额不足");
        }

        ActivityVoucher voucher = ensureVoucherForRegistration(registration);
        registration.setVoucherId(voucher.getId());
        ActivityRegistrationStatusDTO status = buildRegistrationStatus(registration, voucher, activity);
        if (isFirstComeFirstServedMode(activity)) {
            stringRedisTemplate.opsForValue().decrement(activityFrozenKey(activity.getId()));
        }
        cacheFinalRegistrationStatus(activity.getId(), registration.getUserId(), status);
        refreshActivityCacheState(activity.getId(), false);
        embeddingTaskService.touchUser(registration.getUserId(), "REGISTRATION_SUCCESS");
        incrementRecommendationVersion(registration.getUserId());
        publishRegistrationResult(registration.getUserId(), status);
        notifyRegistrationSuccess(activity, registration.getUserId());
        recordActivityAdminReview(activity, registration, "REGISTRATION", "APPROVED", null);
    }

    private void rejectRegistration(Activity activity, ActivityRegistration registration, ReviewActionDTO dto) {
        String reason = StrUtil.blankToDefault(StrUtil.trim(dto.getReviewRemark()), "主办方未通过报名申请");
        ActivityRegistration update = new ActivityRegistration();
        update.setId(registration.getId());
        update.setStatus(REGISTRATION_FAILED);
        update.setFailReason(reason);
        update.setConfirmTime(LocalDateTime.now());
        activityRegistrationMapper.updateById(update);
        if (isFirstComeFirstServedMode(activity)) {
            rollbackReservation(activity.getId(), registration.getUserId(), registration.getRequestId(), reason);
        } else {
            cacheFinalRegistrationStatus(activity.getId(), registration.getUserId(),
                    buildRegistrationStatus(update.setActivityId(activity.getId()).setUserId(registration.getUserId()), null, activity));
        }
        refreshActivityCacheState(activity.getId(), false);
        ActivityRegistrationStatusDTO status = resolveRegistrationStatus(activity.getId(), registration.getUserId());
        publishRegistrationResult(registration.getUserId(), status);
        notifyRegistrationFailed(activity.getId(), registration.getUserId(), reason);
        recordActivityAdminReview(activity, registration, "REGISTRATION", "REJECTED", reason);
    }

    private void approveCancelRegistration(Activity activity, ActivityRegistration registration) {
        ActivityVoucher voucher = activityVoucherMapper.selectOne(new QueryWrapper<ActivityVoucher>()
                .eq("registration_id", registration.getId()));
        if (voucher != null) {
            activityVoucherMapper.deleteById(voucher.getId());
            if (StrUtil.isNotBlank(voucher.getDisplayCode())) {
                stringRedisTemplate.delete(ACTIVITY_VOUCHER_DISPLAY_KEY + voucher.getDisplayCode());
            }
        }
        ActivityRegistration update = new ActivityRegistration();
        update.setId(registration.getId());
        update.setStatus(REGISTRATION_CANCELED);
        update.setFailReason("退出申请已通过");
        update.setVoucherId(null);
        update.setConfirmTime(LocalDateTime.now());
        activityRegistrationMapper.updateById(update);
        UpdateWrapper<Activity> wrapper = new UpdateWrapper<>();
        wrapper.setSql("registered_count = registered_count - 1")
                .eq("id", activity.getId())
                .gt("registered_count", 0);
        update(null, wrapper);
        if (isFirstComeFirstServedMode(activity)) {
            stringRedisTemplate.opsForSet().remove(activityRegisterUsersKey(activity.getId()), registration.getUserId().toString());
            stringRedisTemplate.opsForValue().increment(activityStockKey(activity.getId()));
        }
        cacheCanceledRegistrationStatus(activity.getId(), registration.getUserId(), registration.getRequestId(), "已退出活动");
        refreshActivityCacheState(activity.getId(), false);
        embeddingTaskService.touchUser(registration.getUserId(), "REGISTRATION_CANCEL");
        incrementRecommendationVersion(registration.getUserId());
        ActivityRegistrationStatusDTO status = resolveRegistrationStatus(activity.getId(), registration.getUserId());
        publishRegistrationResult(registration.getUserId(), status);
        notifyRegistrationCancelApproved(activity, registration.getUserId());
        recordActivityAdminReview(activity, registration, "REGISTRATION_CANCEL", "APPROVED", null);
    }

    private void rejectCancelRegistration(Activity activity, ActivityRegistration registration, ReviewActionDTO dto) {
        String reason = StrUtil.blankToDefault(StrUtil.trim(dto.getReviewRemark()), "主办方未通过退出申请");
        ActivityRegistration update = new ActivityRegistration();
        update.setId(registration.getId());
        update.setStatus(REGISTRATION_SUCCESS);
        update.setFailReason(null);
        update.setConfirmTime(LocalDateTime.now());
        activityRegistrationMapper.updateById(update);
        registration.setStatus(REGISTRATION_SUCCESS);
        registration.setFailReason(null);
        registration.setConfirmTime(update.getConfirmTime());
        ActivityVoucher voucher = activityVoucherMapper.selectOne(new QueryWrapper<ActivityVoucher>()
                .eq("registration_id", registration.getId()));
        ActivityRegistrationStatusDTO status = buildRegistrationStatus(registration, voucher, activity);
        cacheFinalRegistrationStatus(activity.getId(), registration.getUserId(), status);
        refreshActivityCacheState(activity.getId(), false);
        publishRegistrationResult(registration.getUserId(), status);
        notifyRegistrationCancelRejected(activity, registration.getUserId(), reason);
        recordActivityAdminReview(activity, registration, "REGISTRATION_CANCEL", "REJECTED", reason);
    }

    private void recordActivityAdminReview(Activity activity, ActivityRegistration registration,
                                           String bizType, String action, String remark) {
        if (activity == null || registration == null) {
            return;
        }
        String targetName = queryUserNameMap(Collections.singletonList(registration.getUserId()))
                .getOrDefault(registration.getUserId(), "");
        reviewRecordService.record(
                RbacConstants.ROLE_ACTIVITY_ADMIN,
                "ACTIVITY_ADMIN",
                bizType,
                registration.getId(),
                activity.getTitle(),
                registration.getUserId(),
                targetName,
                action,
                remark
        );
    }

    private void attachVoucherUserInfo(List<ActivityVoucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        Map<Long, String> userNameMap = queryUserNameMap(vouchers.stream()
                .map(ActivityVoucher::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        for (ActivityVoucher voucher : vouchers) {
            voucher.setUserNickName(userNameMap.get(voucher.getUserId()));
        }
    }

    private void syncRemainingSlots(List<Activity> activities) {
        // 报名改为主办方审核后，公开展示人数只代表已审核通过人数。
        // Redis stock 包含待审核冻结名额，只用于容量控制，不能反推展示人数。
    }

    private CachedActivityPage queryCachedActivityPage(String keyword,
                                                       String category,
                                                       Integer status,
                                                       String location,
                                                       String organizerName,
                                                       String stageFilter,
                                                       String sortBy,
                                                       LocalDateTime startTimeFrom,
                                                       LocalDateTime startTimeTo,
                                                       Integer current,
                                                       Integer pageSize) {
        int normalizedPageSize = normalizePageSize(pageSize);
        int currentPage = current == null || current < 1 ? 1 : current;
        Integer targetStatus = status == null ? null : status;
        String normalizedStageFilter = normalizeStageFilter(stageFilter);
        String params = StrUtil.join("|",
                "status=" + (targetStatus == null ? "PUBLIC" : targetStatus),
                "category=" + StrUtil.blankToDefault(category, ""),
                "keyword=" + StrUtil.blankToDefault(keyword, ""),
                "location=" + StrUtil.blankToDefault(location, ""),
                "organizerName=" + StrUtil.blankToDefault(organizerName, ""),
                "stageFilter=" + StrUtil.blankToDefault(normalizedStageFilter, ""),
                "sortBy=" + StrUtil.blankToDefault(sortBy, ""),
                "startTimeFrom=" + (startTimeFrom == null ? "" : startTimeFrom),
                "startTimeTo=" + (startTimeTo == null ? "" : startTimeTo),
                "current=" + currentPage,
                "pageSize=" + normalizedPageSize);
        String key = CACHE_ACTIVITY_LIST_KEY + DigestUtil.md5Hex(params);
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cacheJson)) {
            return parseCachedActivityPage(cacheJson);
        }

        QueryWrapper<Activity> wrapper = new QueryWrapper<>();
        wrapper.in(targetStatus == null, "status", STATUS_PUBLISHED, STATUS_OFFLINE_PENDING_REVIEW)
                .eq(targetStatus != null, "status", targetStatus)
                .and(StrUtil.isNotBlank(keyword), query -> applyActivityKeywordQuery(query, keyword))
                .eq(StrUtil.isNotBlank(category), "category", category)
                .like(StrUtil.isNotBlank(location), "location", location)
                .like(StrUtil.isNotBlank(organizerName), "organizer_name", organizerName)
                .ge(startTimeFrom != null, "event_start_time", startTimeFrom)
                .le(startTimeTo != null, "event_start_time", startTimeTo);
        applyMysqlStageFilter(wrapper, normalizedStageFilter, LocalDateTime.now());
        applyMysqlFallbackSort(wrapper, sortBy);
        Page<Activity> page = page(new Page<>(currentPage, normalizedPageSize), wrapper);

        Map<String, Object> cacheValue = new HashMap<>(2);
        cacheValue.put("total", page.getTotal());
        cacheValue.put("records", page.getRecords());
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(cacheValue), CACHE_ACTIVITY_LIST_TTL, TimeUnit.MINUTES);
        return new CachedActivityPage(page.getRecords(), page.getTotal());
    }

    private String normalizeStageFilter(String stageFilter) {
        String normalized = StrUtil.trimToEmpty(stageFilter).toUpperCase();
        if (STAGE_FILTER_REGISTRATION_OPEN.equals(normalized)
                || STAGE_FILTER_REGISTRATION_NOT_OPEN.equals(normalized)
                || STAGE_FILTER_IN_PROGRESS.equals(normalized)
                || STAGE_FILTER_FINISHED.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private void applyMysqlStageFilter(QueryWrapper<Activity> wrapper, String stageFilter, LocalDateTime now) {
        if (wrapper == null || StrUtil.isBlank(stageFilter) || now == null) {
            return;
        }
        switch (stageFilter) {
            case STAGE_FILTER_REGISTRATION_OPEN:
                wrapper.and(query -> query
                        .and(part -> part.isNull("registration_start_time").or().le("registration_start_time", now))
                        .and(part -> part.isNull("registration_end_time").or().ge("registration_end_time", now))
                        .apply("(max_participants is null or registered_count is null or registered_count < max_participants)"));
                return;
            case STAGE_FILTER_REGISTRATION_NOT_OPEN:
                wrapper.and(query -> query
                        .and(part -> part.isNull("event_start_time").or().gt("event_start_time", now))
                        .and(part -> part
                                .gt("registration_start_time", now)
                                .or(inner -> inner
                                        .and(x -> x.isNull("registration_start_time").or().le("registration_start_time", now))
                                        .and(x -> x.lt("registration_end_time", now)
                                                .or()
                                                .apply("(max_participants is not null and registered_count is not null and registered_count >= max_participants)")))));
                return;
            case STAGE_FILTER_IN_PROGRESS:
                wrapper.and(query -> query
                        .isNotNull("event_start_time")
                        .le("event_start_time", now)
                        .and(part -> part.isNull("event_end_time").or().gt("event_end_time", now)));
                return;
            case STAGE_FILTER_FINISHED:
                wrapper.isNotNull("event_end_time").le("event_end_time", now);
                return;
            default:
                return;
        }
    }

    private List<ActivityRegistration> filterMyRegistrations(List<ActivityRegistration> records, String filter) {
        String targetFilter = StrUtil.blankToDefault(StrUtil.trim(filter), MY_REGISTRATION_FILTER_ALL).toUpperCase();
        if (MY_REGISTRATION_FILTER_ALL.equals(targetFilter)) {
            return records;
        }
        LocalDateTime now = LocalDateTime.now();
        return records.stream()
                .filter(record -> matchesMyRegistrationFilter(record, targetFilter, now))
                .collect(Collectors.toList());
    }

    private List<ActivityRegistration> filterMyRegistrationsByKeyword(List<ActivityRegistration> records, String keyword) {
        if (records == null || records.isEmpty() || StrUtil.isBlank(keyword)) {
            return records;
        }
        String normalizedKeyword = StrUtil.trim(keyword).toLowerCase();
        return records.stream()
                .filter(record -> matchesMyRegistrationKeyword(record, normalizedKeyword))
                .collect(Collectors.toList());
    }

    private boolean matchesMyRegistrationKeyword(ActivityRegistration record, String keyword) {
        if (record == null || StrUtil.isBlank(keyword)) {
            return false;
        }
        return containsIgnoreCase(record.getActivityTitle(), keyword)
                || containsIgnoreCase(record.getCategory(), keyword)
                || containsIgnoreCase(record.getLocation(), keyword)
                || containsIgnoreCase(record.getOrganizerName(), keyword)
                || containsIgnoreCase(record.getStatusText(), keyword)
                || containsIgnoreCase(record.getFailReason(), keyword)
                || containsIgnoreCase(record.getVoucherDisplayCode(), keyword)
                || containsIgnoreCase(record.getRequestId(), keyword);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return StrUtil.isNotBlank(source) && source.toLowerCase().contains(keyword);
    }

    private Result queryMyRegistrationsWithSearch(String filter, String keyword, Integer current, Integer pageSize) {
        QueryWrapper<ActivityRegistration> wrapper = new QueryWrapper<ActivityRegistration>()
                .eq("user_id", UserHolder.getUser().getId())
                .orderByDesc("create_time");
        List<ActivityRegistration> records = activityRegistrationMapper.selectList(wrapper);
        enrichRegistrationActivities(records);
        enrichRegistrationVoucherInfo(records);
        List<ActivityRegistration> filteredRecords = filterMyRegistrations(records, filter);
        if (StrUtil.isNotBlank(keyword) && activitySearchService.isAvailable()) {
            List<Long> matchedActivityIds = queryMatchedActivityIdsByKeyword(keyword);
            filteredRecords = mergeRegistrationKeywordMatches(filteredRecords, keyword, matchedActivityIds);
        } else {
            filteredRecords = filterMyRegistrationsByKeyword(filteredRecords, keyword);
        }
        return paginateRegistrations(filteredRecords, current, pageSize);
    }

    private Result queryMyRegistrationsFromMysql(String filter, String keyword, Integer current, Integer pageSize) {
        QueryWrapper<ActivityRegistration> wrapper = new QueryWrapper<ActivityRegistration>()
                .eq("user_id", UserHolder.getUser().getId())
                .orderByDesc("create_time");
        List<ActivityRegistration> records = activityRegistrationMapper.selectList(wrapper);
        enrichRegistrationActivities(records);
        enrichRegistrationVoucherInfo(records);
        List<ActivityRegistration> filteredRecords = filterMyRegistrations(records, filter);
        filteredRecords = filterMyRegistrationsByKeyword(filteredRecords, keyword);
        return paginateRegistrations(filteredRecords, current, pageSize);
    }

    private Result paginateRegistrations(List<ActivityRegistration> records, Integer current, Integer pageSize) {
        int normalizedPageSize = normalizePageSize(pageSize);
        int currentPage = current == null || current < 1 ? 1 : current;
        int fromIndex = Math.min((currentPage - 1) * normalizedPageSize, records.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, records.size());
        List<ActivityRegistration> pagedRecords = records.subList(fromIndex, toIndex);
        return Result.ok(pagedRecords, (long) records.size());
    }

    private List<ActivityRegistration> mergeRegistrationKeywordMatches(List<ActivityRegistration> records,
                                                                       String keyword,
                                                                       List<Long> matchedActivityIds) {
        if (records == null || records.isEmpty() || StrUtil.isBlank(keyword)) {
            return records;
        }
        List<ActivityRegistration> mysqlMatches = filterMyRegistrationsByKeyword(records, keyword);
        if (matchedActivityIds == null || matchedActivityIds.isEmpty()) {
            return mysqlMatches;
        }
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < matchedActivityIds.size(); i++) {
            orderMap.putIfAbsent(matchedActivityIds.get(i), i);
        }
        List<ActivityRegistration> esMatches = records.stream()
                .filter(record -> record != null && record.getActivityId() != null && orderMap.containsKey(record.getActivityId()))
                .sorted(Comparator
                        .comparingInt((ActivityRegistration record) -> orderMap.getOrDefault(record.getActivityId(), Integer.MAX_VALUE))
                        .thenComparing(ActivityRegistration::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        Set<Long> seenIds = esMatches.stream()
                .map(ActivityRegistration::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ActivityRegistration> merged = new ArrayList<>(esMatches);
        for (ActivityRegistration record : mysqlMatches) {
            if (record == null || record.getId() == null || seenIds.contains(record.getId())) {
                continue;
            }
            merged.add(record);
        }
        return merged;
    }

    private List<Long> queryMatchedActivityIdsByKeyword(String keyword) {
        List<Long> activityIds = new ArrayList<>();
        int current = 1;
        int pageSize = 100;
        while (true) {
            ActivitySearchPageDTO searchPage = activitySearchService.searchActivitiesByKeyword(keyword, current, pageSize);
            List<Activity> activities = searchPage.getRecords();
            if (activities == null || activities.isEmpty()) {
                break;
            }
            for (Activity activity : activities) {
                if (activity != null && activity.getId() != null) {
                    activityIds.add(activity.getId());
                }
            }
            Long total = searchPage.getTotal();
            if (activities.size() < pageSize || total == null || activityIds.size() >= total) {
                break;
            }
            current++;
        }
        return activityIds;
    }

    private QueryWrapper<Activity> applyActivityKeywordQuery(QueryWrapper<Activity> wrapper, String keyword) {
        if (wrapper == null || StrUtil.isBlank(keyword)) {
            return wrapper;
        }
        List<Long> matchedTagActivityIds = queryActivityIdsByTagKeyword(keyword);
        wrapper.like("title", keyword)
                .or()
                .like("summary", keyword)
                .or()
                .like("content", keyword)
                .or()
                .like("organizer_name", keyword)
                .or()
                .like("category", keyword)
                .or()
                .like("location", keyword);
        if (!matchedTagActivityIds.isEmpty()) {
            wrapper.or().in("id", matchedTagActivityIds);
        }
        return wrapper;
    }

    private List<Long> queryActivityIdsByTagKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        List<ActivityTag> matchedTags = activityTagMapper.selectList(new QueryWrapper<ActivityTag>()
                .like("name", keyword)
                .eq("status", 1));
        if (matchedTags == null || matchedTags.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = matchedTags.stream().map(ActivityTag::getId).collect(Collectors.toList());
        return activityTagRelationMapper.selectList(new QueryWrapper<ActivityTagRelation>()
                        .in("tag_id", tagIds))
                .stream()
                .map(ActivityTagRelation::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean matchesMyRegistrationFilter(ActivityRegistration record, String filter, LocalDateTime now) {
        if (record == null) {
            return false;
        }
        if (MY_REGISTRATION_FILTER_CANCELED.equals(filter)) {
            return Objects.equals(record.getStatus(), REGISTRATION_CANCELED);
        }
        if (MY_REGISTRATION_FILTER_PENDING_CHECK_IN.equals(filter)) {
            return Objects.equals(record.getStatus(), REGISTRATION_SUCCESS)
                    && !Objects.equals(record.getCheckInStatus(), CHECKED_IN)
                    && (record.getEventEndTime() == null || !now.isAfter(record.getEventEndTime()));
        }
        if (MY_REGISTRATION_FILTER_CHECKED_IN.equals(filter)) {
            return Objects.equals(record.getStatus(), REGISTRATION_SUCCESS)
                    && Objects.equals(record.getCheckInStatus(), CHECKED_IN);
        }
        if (MY_REGISTRATION_FILTER_FINISHED.equals(filter)) {
            return record.getEventEndTime() != null && now.isAfter(record.getEventEndTime());
        }
        return true;
    }

    private List<ActivityCategoryTreeDTO> queryCachedCategories() {
        String key = CACHE_ACTIVITY_CATEGORIES_KEY;
        String categoriesJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(categoriesJson)) {
            return JSONUtil.parseArray(categoriesJson).toList(ActivityCategoryTreeDTO.class);
        }
        List<ActivityCategoryTreeDTO> categories = buildCategoryTree();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(categories), CACHE_ACTIVITY_CATEGORIES_TTL, TimeUnit.MINUTES);
        return categories;
    }

    private List<ActivityCategoryTreeDTO> buildCategoryTree() {
        List<ActivityCategory> categories = activityCategoryMapper.selectList(new QueryWrapper<ActivityCategory>()
                .eq("status", 1)
                .orderByAsc("sort_no")
                .orderByAsc("id"));
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<ActivityTag>> tagMap = activityTagMapper.selectList(new QueryWrapper<ActivityTag>()
                        .eq("status", 1)
                        .orderByAsc("sort_no")
                        .orderByAsc("id"))
                .stream()
                .collect(Collectors.groupingBy(ActivityTag::getCategoryId, LinkedHashMap::new, Collectors.toList()));
        List<ActivityCategoryTreeDTO> result = new ArrayList<>(categories.size());
        for (ActivityCategory category : categories) {
            ActivityCategoryTreeDTO dto = new ActivityCategoryTreeDTO();
            dto.setId(category.getId());
            dto.setName(category.getName());
            dto.setSortNo(category.getSortNo());
            List<ActivityTagOptionDTO> tags = tagMap.getOrDefault(category.getId(), Collections.emptyList()).stream()
                    .map(item -> toTagOption(item, category.getName()))
                    .collect(Collectors.toList());
            dto.setTags(tags);
            result.add(dto);
        }
        return result;
    }

    private void attachActivityTags(List<Activity> activities) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        List<Long> activityIds = activities.stream()
                .map(Activity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (activityIds.isEmpty()) {
            return;
        }
        List<ActivityTagRelation> relations = activityTagRelationMapper.selectList(new QueryWrapper<ActivityTagRelation>()
                .in("activity_id", activityIds)
                .orderByAsc("id"));
        Map<Long, List<ActivityTag>> activityTagMap = buildActivityTagMap(relations);
        for (Activity activity : activities) {
            List<ActivityTag> tags = new ArrayList<>(activityTagMap.getOrDefault(activity.getId(), Collections.emptyList()));
            activity.setTags(tags);
            activity.setTagIds(tags.stream().map(ActivityTag::getId).collect(Collectors.toList()));
            activity.setDisplayCategory(buildDisplayCategory(activity.getCategory(), tags));
            activity.setCustomCategory(null);
        }
    }

    private Map<Long, List<ActivityTag>> buildActivityTagMap(List<ActivityTagRelation> relations) {
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> tagIdsByActivity = new LinkedHashMap<>();
        for (ActivityTagRelation relation : relations) {
            tagIdsByActivity.computeIfAbsent(relation.getActivityId(), key -> new ArrayList<>()).add(relation.getTagId());
        }
        List<Long> tagIds = tagIdsByActivity.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
        Map<Long, ActivityTag> tagMap = queryTagMap(tagIds);
        Map<Long, List<ActivityTag>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Long>> entry : tagIdsByActivity.entrySet()) {
            List<ActivityTag> tags = entry.getValue().stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            result.put(entry.getKey(), tags);
        }
        return result;
    }

    private Map<Long, ActivityTag> queryTagMap(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ActivityTag> tags = activityTagMapper.selectList(new QueryWrapper<ActivityTag>()
                .in("id", tagIds)
                .eq("status", 1)
                .orderByAsc("sort_no")
                .orderByAsc("id"));
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> categoryNameMap = queryCategoryNameMap(tags.stream()
                .map(ActivityTag::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        for (ActivityTag tag : tags) {
            tag.setCategoryName(categoryNameMap.get(tag.getCategoryId()));
        }
        return tags.stream().collect(Collectors.toMap(ActivityTag::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, String> queryCategoryNameMap(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return activityCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ActivityCategory::getId, ActivityCategory::getName, (a, b) -> a));
    }

    private ActivityTagOptionDTO toTagOption(ActivityTag tag, String categoryName) {
        ActivityTagOptionDTO dto = new ActivityTagOptionDTO();
        dto.setId(tag.getId());
        dto.setCategoryId(tag.getCategoryId());
        dto.setCategoryName(categoryName);
        dto.setName(tag.getName());
        dto.setSortNo(tag.getSortNo());
        return dto;
    }

    private String buildDisplayCategory(String category, List<ActivityTag> tags) {
        if (StrUtil.isBlank(category)) {
            return "未分类";
        }
        if (tags == null || tags.isEmpty()) {
            return category;
        }
        return category + " / " + tags.stream()
                .map(ActivityTag::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("、"));
    }

    private void replaceActivityTags(Long activityId, List<Long> tagIds) {
        if (activityId == null) {
            return;
        }
        activityTagRelationMapper.delete(new QueryWrapper<ActivityTagRelation>().eq("activity_id", activityId));
        for (Long tagId : normalizeTagIds(tagIds)) {
            ActivityTagRelation relation = new ActivityTagRelation();
            relation.setActivityId(activityId);
            relation.setTagId(tagId);
            activityTagRelationMapper.insert(relation);
        }
    }

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tagIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<ActivityTagOptionDTO> queryCachedUserPreferenceTags(Long userId) {
        String key = USER_PREFERENCE_TAGS_KEY + userId;
        String cachedJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cachedJson)) {
            return JSONUtil.parseArray(cachedJson).toList(ActivityTagOptionDTO.class);
        }
        List<UserPreferenceTag> preferences = userPreferenceTagMapper.selectList(new QueryWrapper<UserPreferenceTag>()
                .eq("user_id", userId)
                .eq("source", ActivityCategoryConstants.PREFERENCE_SOURCE_MANUAL)
                .orderByAsc("id"));
        List<Long> tagIds = preferences.stream().map(UserPreferenceTag::getTagId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, ActivityTag> tagMap = queryTagMap(tagIds);
        List<ActivityTagOptionDTO> result = tagIds.stream()
                .map(tagMap::get)
                .filter(Objects::nonNull)
                .map(item -> toTagOption(item, item.getCategoryName()))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(result), USER_PREFERENCE_TAGS_TTL, TimeUnit.MINUTES);
        return result;
    }

    private void evictUserPreferenceCache(Long userId) {
        if (userId != null) {
            stringRedisTemplate.delete(USER_PREFERENCE_TAGS_KEY + userId);
        }
    }

    private void incrementRecommendationVersion(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().increment(RECOMMENDATION_USER_VERSION_KEY + userId + ":version");
        } catch (Exception e) {
            log.warn("递增推荐版本失败 userId={}", userId, e);
        }
    }

    private void incrementGlobalRecommendationVersion() {
        try {
            stringRedisTemplate.opsForValue().increment(RECOMMENDATION_GLOBAL_VERSION_KEY);
        } catch (Exception e) {
            log.warn("递增全局推荐版本失败", e);
        }
    }

    private void applyMysqlFallbackSort(QueryWrapper<Activity> wrapper, String sortBy) {
        String targetSort = StrUtil.blankToDefault(sortBy, "composite");
        if ("publishTimeDesc".equals(targetSort)) {
            wrapper.orderByDesc("create_time");
            return;
        }
        if ("signupCountDesc".equals(targetSort) || "heatScoreDesc".equals(targetSort)) {
            wrapper.orderByDesc("registered_count").orderByDesc("create_time");
            return;
        }
        wrapper.orderByAsc("event_start_time").orderByDesc("create_time");
    }

    private CachedActivityPage parseCachedActivityPage(String cacheJson) {
        JSONObject jsonObject = JSONUtil.parseObj(cacheJson);
        JSONArray recordsArray = jsonObject.getJSONArray("records");
        List<Activity> records = recordsArray == null ? new ArrayList<>() : recordsArray.toList(Activity.class);
        Long total = jsonObject.getLong("total", 0L);
        return new CachedActivityPage(records, total);
    }

    private Activity loadActivityDetail(Long id, boolean cacheOnly) {
        if (id == null) {
            return null;
        }
        if (cacheOnly) {
            String cacheJson = stringRedisTemplate.opsForValue().get(CACHE_ACTIVITY_DETAIL_KEY + id);
            if (StrUtil.isBlank(cacheJson)) {
                return null;
            }
            Activity cached = JSONUtil.toBean(cacheJson, Activity.class);
            attachActivityTags(Collections.singletonList(cached));
            return cached;
        }
        Activity activity = cacheClient.queryWithPassThrough(
                CACHE_ACTIVITY_DETAIL_KEY,
                id,
                Activity.class,
                this::getById,
                CACHE_ACTIVITY_DETAIL_TTL,
                TimeUnit.MINUTES
        );
        attachActivityTags(Collections.singletonList(activity));
        return activity;
    }

    private Result buildActivityDetailResult(Activity activity, boolean fallbackMode) {
        if (activity == null) {
            return fallbackMode ? Result.fail("当前活动访问人数较多，请稍后刷新") : Result.fail("活动不存在");
        }
        UserDTO currentUser = UserHolder.getUser();
        boolean canViewUnpublished = currentUser != null
                && (Objects.equals(activity.getCreatorId(), currentUser.getId())
                || AuthorizationUtils.hasPermission(currentUser, RbacConstants.PERM_ACTIVITY_APPROVE));
        if (!isPublicActivityStatus(activity.getStatus()) && !canViewUnpublished) {
            return Result.fail("活动尚未公开");
        }
        syncRemainingSlots(Collections.singletonList(activity));
        enrichActivities(Collections.singletonList(activity), currentUser);
        return Result.ok(activity);
    }

    private void refreshActivityCacheState(Long activityId, boolean evictCategoryCache) {
        Activity latest = getById(activityId);
        incrementGlobalRecommendationVersion();
        if (latest == null) {
            evictActivityCache(activityId, evictCategoryCache);
            dispatchActivitySearchSyncEvent(activityId, "DELETE");
            return;
        }
        evictActivityCache(activityId, evictCategoryCache);
        if (isFirstComeFirstServedMode(latest)) {
            initActivityRegistrationCache(latest);
        }
        dispatchActivitySearchSyncEvent(activityId, evictCategoryCache ? "UPSERT" : "STATE_REFRESH");
    }

    private void dispatchActivitySearchSyncEvent(Long activityId, String trigger) {
        if (activityId == null || StrUtil.isBlank(activitySearchProperties.getSync().getTopic())) {
            return;
        }
        Runnable action = () -> sendActivitySearchSyncEvent(activityId, trigger);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void sendActivitySearchSyncEvent(Long activityId, String trigger) {
        ActivitySearchSyncEventDTO event = new ActivitySearchSyncEventDTO();
        event.setActivityId(activityId);
        event.setTrigger(trigger);
        event.setEventTime(LocalDateTime.now());
        try {
            rocketMQTemplate.convertAndSend(activitySearchProperties.getSync().getTopic(), event);
        } catch (Exception e) {
            log.warn("发送活动搜索索引同步消息失败，改走本地补偿 activityId={}, trigger={}",
                    activityId, trigger, e);
            try {
                if (activitySearchService.isAvailable()) {
                    activitySearchService.syncActivity(activityId);
                }
            } catch (Exception syncException) {
                log.error("本地补偿同步活动搜索索引失败 activityId={}", activityId, syncException);
            }
        }
    }

    private void notifyActivitySubmitted(Activity activity) {
        if (activity == null || activity.getId() == null) {
            return;
        }
        notificationService.notifyUsers(
                Collections.singletonList(activity.getCreatorId()),
                "活动已提交审核",
                "你提交的“" + activity.getTitle() + "”已进入平台审核，请等待处理。",
                "ACTIVITY_SUBMITTED",
                "ACTIVITY",
                activity.getId()
        );
        notificationService.notifyRole(
                RbacConstants.ROLE_PLATFORM_ADMIN,
                "有新的待审核活动",
                "主办方提交了活动“" + activity.getTitle() + "”，请及时审核。",
                "ACTIVITY_REVIEW_PENDING",
                "ACTIVITY",
                activity.getId()
        );
    }

    private void notifyActivityReviewResult(Activity activity, ReviewActionDTO dto) {
        if (activity == null || activity.getCreatorId() == null || dto == null) {
            return;
        }
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        String title = approved ? "活动审核通过" : "活动审核未通过";
        StringBuilder content = new StringBuilder();
        content.append("你提交的“").append(activity.getTitle()).append("”")
                .append(approved ? "已审核通过并发布。" : "未通过平台审核。");
        if (!approved && StrUtil.isNotBlank(dto.getReviewRemark())) {
            content.append("原因：").append(dto.getReviewRemark());
        }
        notificationService.notifyUsers(
                Collections.singletonList(activity.getCreatorId()),
                title,
                content.toString(),
                approved ? "ACTIVITY_APPROVED" : "ACTIVITY_REJECTED",
                "ACTIVITY",
                activity.getId()
        );
    }

    private void notifyActivityOfflineReviewResult(Activity activity, ReviewActionDTO dto) {
        if (activity == null || activity.getCreatorId() == null || dto == null) {
            return;
        }
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        String title = approved ? "活动下架申请通过" : "活动下架申请未通过";
        StringBuilder content = new StringBuilder();
        content.append("你提交的“").append(activity.getTitle()).append("”下架申请")
                .append(approved ? "已通过，活动已下架。" : "未通过，活动继续发布。");
        if (!approved && StrUtil.isNotBlank(dto.getReviewRemark())) {
            content.append("原因：").append(dto.getReviewRemark());
        }
        notificationService.notifyUsers(
                Collections.singletonList(activity.getCreatorId()),
                title,
                content.toString(),
                approved ? "ACTIVITY_OFFLINE_APPLY_APPROVED" : "ACTIVITY_OFFLINE_APPLY_REJECTED",
                "ACTIVITY",
                activity.getId()
        );
    }

    private void notifyActivityLocationChanged(Activity activity, String oldLocation) {
        if (activity == null || activity.getId() == null) {
            return;
        }
        List<ActivityRegistration> registrations = activityRegistrationMapper.selectList(new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activity.getId())
                .eq("status", REGISTRATION_SUCCESS));
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        List<Long> userIds = registrations.stream()
                .map(ActivityRegistration::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        notificationService.notifyUsers(
                userIds,
                "活动地点变更",
                "你报名的“" + activity.getTitle() + "”地点已由“"
                        + StrUtil.blankToDefault(oldLocation, "未填写") + "”变更为“" + activity.getLocation() + "”。",
                "ACTIVITY_LOCATION_CHANGED",
                "ACTIVITY",
                activity.getId()
        );
    }

    private void notifyRegistrationRequested(Activity activity, Long userId) {
        if (activity == null || activity.getCreatorId() == null || userId == null) {
            return;
        }
        String userName = queryUserNameMap(Collections.singletonList(userId)).getOrDefault(userId, "有用户");
        notificationService.notifyUsers(
                Collections.singletonList(activity.getCreatorId()),
                "有新的报名申请",
                userName + " 申请报名“" + activity.getTitle() + "”，请及时审核。",
                "REGISTRATION_REVIEW_PENDING",
                "REGISTRATION",
                activity.getId()
        );
    }

    private void notifyRegistrationSuccess(Activity activity, Long userId) {
        if (activity == null || userId == null) {
            return;
        }
        boolean auditMode = isAuditRequiredMode(activity);
        notificationService.notifyUsers(
                Collections.singletonList(userId),
                auditMode ? "报名审核通过" : "报名成功",
                auditMode ? "你报名的“" + activity.getTitle() + "”已通过主办方审核。"
                        : "你已成功报名“" + activity.getTitle() + "”。",
                "REGISTRATION_SUCCESS",
                "REGISTRATION",
                activity.getId()
        );
    }

    private void notifyRegistrationFailed(Long activityId, Long userId, String reason) {
        if (activityId == null || userId == null) {
            return;
        }
        Activity activity = getById(activityId);
        String activityTitle = activity == null ? "该活动" : "“" + activity.getTitle() + "”";
        boolean auditMode = activity == null || isAuditRequiredMode(activity);
        notificationService.notifyUsers(
                Collections.singletonList(userId),
                auditMode ? "报名审核未通过" : "报名失败",
                auditMode ? "你报名的" + activityTitle + "未通过审核。" + StrUtil.blankToDefault(reason, "")
                        : "你报名的" + activityTitle + "失败。" + StrUtil.blankToDefault(reason, ""),
                "REGISTRATION_FAILED",
                "REGISTRATION",
                activityId
        );
    }

    private void notifyRegistrationCancelRequested(Activity activity, ActivityRegistration registration) {
        if (activity == null || registration == null || activity.getCreatorId() == null) {
            return;
        }
        String userName = queryUserNameMap(Collections.singletonList(registration.getUserId()))
                .getOrDefault(registration.getUserId(), "有用户");
        notificationService.notifyUsers(
                Collections.singletonList(activity.getCreatorId()),
                "有新的退出申请",
                userName + " 申请退出“" + activity.getTitle() + "”，请及时审核。",
                "REGISTRATION_CANCEL_REVIEW_PENDING",
                "REGISTRATION",
                activity.getId()
        );
    }

    private void notifyRegistrationCancelApproved(Activity activity, Long userId) {
        if (activity == null || userId == null) {
            return;
        }
        notificationService.notifyUsers(
                Collections.singletonList(userId),
                "退出申请已通过",
                "你退出“" + activity.getTitle() + "”的申请已通过。",
                "REGISTRATION_CANCEL_APPROVED",
                "REGISTRATION",
                activity.getId()
        );
    }

    private void notifyRegistrationCancelRejected(Activity activity, Long userId, String reason) {
        if (activity == null || userId == null) {
            return;
        }
        notificationService.notifyUsers(
                Collections.singletonList(userId),
                "退出申请未通过",
                "你退出“" + activity.getTitle() + "”的申请未通过。" + StrUtil.blankToDefault(reason, ""),
                "REGISTRATION_CANCEL_REJECTED",
                "REGISTRATION",
                activity.getId()
        );
    }

    private void ensureActivityRegistrationCache(Activity activity) {
        if (!isFirstComeFirstServedMode(activity)) {
            return;
        }
        Boolean exists = stringRedisTemplate.hasKey(activityMetaKey(activity.getId()));
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        initActivityRegistrationCache(activity);
    }

    private void initActivityRegistrationCache(Activity activity) {
        if (!isFirstComeFirstServedMode(activity)) {
            return;
        }
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("status", String.valueOf(activity.getStatus() == null ? 0 : activity.getStatus()));
        meta.put("registrationStartEpoch", String.valueOf(toEpoch(activity.getRegistrationStartTime())));
        meta.put("registrationEndEpoch", String.valueOf(toEpoch(activity.getRegistrationEndTime())));
        meta.put("maxParticipants", String.valueOf(activity.getMaxParticipants() == null ? 0 : activity.getMaxParticipants()));
        stringRedisTemplate.opsForHash().putAll(activityMetaKey(activity.getId()), meta);

        List<ActivityRegistration> registrations = activityRegistrationMapper.selectList(new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activity.getId()));
        int occupiedCount = (int) registrations.stream()
                .filter(item -> Objects.equals(item.getStatus(), REGISTRATION_SUCCESS)
                        || Objects.equals(item.getStatus(), REGISTRATION_CANCEL_PENDING))
                .count();
        int pendingCount = (int) registrations.stream()
                .filter(item -> Objects.equals(item.getStatus(), REGISTRATION_PENDING))
                .count();
        int maxParticipants = activity.getMaxParticipants() == null ? 0 : activity.getMaxParticipants();
        int remainingSlots = Math.max(maxParticipants - occupiedCount - pendingCount, 0);
        stringRedisTemplate.opsForValue().set(activityStockKey(activity.getId()), String.valueOf(remainingSlots));
        stringRedisTemplate.opsForValue().set(activityFrozenKey(activity.getId()), String.valueOf(Math.max(pendingCount, 0)));

        String usersKey = activityRegisterUsersKey(activity.getId());
        stringRedisTemplate.delete(usersKey);
        if (!registrations.isEmpty()) {
            String[] userIds = registrations.stream()
                    .filter(item -> Objects.equals(item.getStatus(), REGISTRATION_SUCCESS)
                            || Objects.equals(item.getStatus(), REGISTRATION_PENDING)
                            || Objects.equals(item.getStatus(), REGISTRATION_CANCEL_PENDING))
                    .map(ActivityRegistration::getUserId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toArray(String[]::new);
            if (userIds.length > 0) {
                stringRedisTemplate.opsForSet().add(usersKey, userIds);
            }
            Map<Long, ActivityVoucher> voucherMap = activityVoucherMapper.selectList(new QueryWrapper<ActivityVoucher>()
                            .eq("activity_id", activity.getId()))
                    .stream()
                    .collect(Collectors.toMap(ActivityVoucher::getRegistrationId, v -> v, (a, b) -> a));
            for (ActivityRegistration registration : registrations) {
                ActivityUserStateCache state = buildUserState(registration, voucherMap.get(registration.getId()), activity);
                cacheUserRegistrationState(activity.getId(), registration.getUserId(), state);
            }
        }
    }

    @Transactional
    public void confirmRegistration(ActivityRegistrationEventDTO event) {
        if (event == null || event.getActivityId() == null || event.getUserId() == null || StrUtil.isBlank(event.getRequestId())) {
            return;
        }
        Long activityId = event.getActivityId();
        Long userId = event.getUserId();
        String requestId = event.getRequestId();
        Activity activity = getById(activityId);
        if (activity == null || !isPublicActivityStatus(activity.getStatus()) || !isFirstComeFirstServedMode(activity)) {
            finalizeRegistrationFailure(activityId, userId, requestId, "活动不存在或已下线");
            return;
        }
        try {
            ActivityRegistrationStatusDTO status = transactionTemplate.execute(tx -> saveRegistrationRecord(activity, userId, requestId));
            if (status == null) {
                finalizeRegistrationFailure(activityId, userId, requestId, "报名失败，请稍后重试");
                return;
            }
            publishRegistrationResult(userId, status);
            notifyRegistrationSuccess(activity, userId);
        } catch (Exception e) {
            log.error("活动报名确认失败 activityId={}, userId={}, requestId={}", activityId, userId, requestId, e);
            finalizeRegistrationFailure(activityId, userId, requestId, "报名失败，请稍后重试");
        }
    }

    private ActivityRegistrationStatusDTO saveRegistrationRecord(Activity activity, Long userId, String requestId) {
        ActivityRegistration existing = activityRegistrationMapper.selectOne(new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activity.getId())
                .eq("user_id", userId));
        ActivityRegistration registration;
        if (existing != null && Objects.equals(existing.getStatus(), REGISTRATION_SUCCESS)) {
            ActivityVoucher voucher = ensureVoucherForRegistration(existing);
            ActivityRegistrationStatusDTO status = buildRegistrationStatus(existing, voucher, activity);
            cacheFinalRegistrationStatus(activity.getId(), userId, status);
            refreshActivityCacheState(activity.getId(), false);
            return status;
        }
        if (existing != null && Objects.equals(existing.getStatus(), REGISTRATION_PENDING)) {
            ActivityRegistrationStatusDTO status = buildPendingConfirmStatus(existing, activity);
            cacheFinalRegistrationStatus(activity.getId(), userId, status);
            refreshActivityCacheState(activity.getId(), false);
            return status;
        }
        if (existing == null) {
            registration = new ActivityRegistration();
            registration.setActivityId(activity.getId());
            registration.setUserId(userId);
            registration.setStatus(REGISTRATION_SUCCESS);
            registration.setRequestId(requestId);
            registration.setFailReason(null);
            registration.setVoucherId(null);
            registration.setCheckInStatus(CHECKED_OUT);
            registration.setConfirmTime(LocalDateTime.now());
            try {
                activityRegistrationMapper.insert(registration);
            } catch (DuplicateKeyException e) {
                existing = activityRegistrationMapper.selectOne(new QueryWrapper<ActivityRegistration>()
                        .eq("activity_id", activity.getId())
                        .eq("user_id", userId));
                if (existing == null) {
                    throw e;
                }
                registration = existing;
            }
        } else {
            registration = existing;
            ActivityRegistration update = new ActivityRegistration();
            update.setId(existing.getId());
            update.setStatus(REGISTRATION_SUCCESS);
            update.setRequestId(requestId);
            update.setFailReason(null);
            update.setCheckInStatus(CHECKED_OUT);
            update.setConfirmTime(LocalDateTime.now());
            activityRegistrationMapper.updateById(update);
            registration.setStatus(REGISTRATION_SUCCESS);
            registration.setRequestId(requestId);
            registration.setFailReason(null);
            registration.setCheckInStatus(CHECKED_OUT);
            registration.setConfirmTime(update.getConfirmTime());
        }

        UpdateWrapper<Activity> wrapper = new UpdateWrapper<>();
        wrapper.setSql("registered_count = registered_count + 1")
                .eq("id", activity.getId())
                .lt("registered_count", activity.getMaxParticipants());
        boolean updated = update(null, wrapper);
        if (!updated) {
            throw new IllegalStateException("活动名额不足");
        }

        ActivityVoucher voucher = ensureVoucherForRegistration(registration);
        registration.setVoucherId(voucher.getId());
        ActivityRegistrationStatusDTO status = buildRegistrationStatus(registration, voucher, activity);
        cacheFinalRegistrationStatus(activity.getId(), userId, status);
        refreshActivityCacheState(activity.getId(), false);
        incrementRecommendationVersion(userId);
        return status;
    }

    private ActivityVoucher ensureVoucherForRegistration(ActivityRegistration registration) {
        ActivityVoucher existingVoucher = activityVoucherMapper.selectOne(new QueryWrapper<ActivityVoucher>()
                .eq("registration_id", registration.getId()));
        if (existingVoucher != null) {
            if (registration.getVoucherId() == null || !Objects.equals(registration.getVoucherId(), existingVoucher.getId())) {
                registration.setVoucherId(existingVoucher.getId());
                activityRegistrationMapper.updateById(registration);
            }
            cacheVoucherDisplayCode(existingVoucher);
            return existingVoucher;
        }
        ActivityVoucher voucher = createVoucherForRegistration(registration);
        registration.setVoucherId(voucher.getId());
        activityRegistrationMapper.updateById(registration);
        return voucher;
    }

    private ActivityVoucher createVoucherForRegistration(ActivityRegistration registration) {
        ActivityVoucher voucher = new ActivityVoucher();
        voucher.setId(redisIdWorker.nextId("activity-voucher"));
        voucher.setActivityId(registration.getActivityId());
        voucher.setRegistrationId(registration.getId());
        voucher.setUserId(registration.getUserId());
        voucher.setStatus(VOUCHER_STATUS_UNUSED);
        voucher.setIssuedTime(LocalDateTime.now());
        for (int i = 0; i < 10; i++) {
            voucher.setDisplayCode(generateDisplayCode());
            try {
                activityVoucherMapper.insert(voucher);
                cacheVoucherDisplayCode(voucher);
                return voucher;
            } catch (DuplicateKeyException e) {
                log.warn("签到凭证展示码冲突，重试生成 activityId={}, registrationId={}",
                        registration.getActivityId(), registration.getId());
            }
        }
        throw new IllegalStateException("签到凭证生成失败，请稍后重试");
    }

    private String generateDisplayCode() {
        StringBuilder builder = new StringBuilder(DISPLAY_CODE_LENGTH);
        for (int i = 0; i < DISPLAY_CODE_LENGTH; i++) {
            int index = (int) (Math.random() * DISPLAY_CODE_CHARS.length());
            builder.append(DISPLAY_CODE_CHARS.charAt(index));
        }
        return builder.toString();
    }

    private void rollbackReservation(Long activityId, Long userId, String requestId, String reason) {
        stringRedisTemplate.opsForValue().increment(activityStockKey(activityId));
        stringRedisTemplate.opsForValue().decrement(activityFrozenKey(activityId));
        stringRedisTemplate.opsForSet().remove(activityRegisterUsersKey(activityId), userId.toString());
        ActivityUserStateCache state = new ActivityUserStateCache();
        state.setStatus(REGISTRATION_STATUS_FAILED);
        state.setRequestId(requestId);
        state.setMessage(reason);
        state.setFailReason(reason);
        state.setRegistered(false);
        cacheUserRegistrationState(activityId, userId, state);
        evictActivityCache(activityId, false);
    }

    private void finalizeRegistrationFailure(Long activityId, Long userId, String requestId, String reason) {
        transactionTemplate.executeWithoutResult(tx -> {
            ActivityRegistration existing = activityRegistrationMapper.selectOne(new QueryWrapper<ActivityRegistration>()
                    .eq("activity_id", activityId)
                    .eq("user_id", userId));
            if (existing == null) {
                ActivityRegistration registration = new ActivityRegistration();
                registration.setActivityId(activityId);
                registration.setUserId(userId);
                registration.setStatus(REGISTRATION_FAILED);
                registration.setRequestId(requestId);
                registration.setFailReason(reason);
                registration.setCheckInStatus(CHECKED_OUT);
                registration.setConfirmTime(LocalDateTime.now());
                activityRegistrationMapper.insert(registration);
            } else {
                ActivityRegistration update = new ActivityRegistration();
                update.setId(existing.getId());
                update.setStatus(REGISTRATION_FAILED);
                update.setRequestId(requestId);
                update.setFailReason(reason);
                update.setConfirmTime(LocalDateTime.now());
                activityRegistrationMapper.updateById(update);
            }
        });
        rollbackReservation(activityId, userId, requestId, reason);
        ActivityRegistrationStatusDTO status = resolveRegistrationStatus(activityId, userId);
        publishRegistrationResult(userId, status);
        notifyRegistrationFailed(activityId, userId, reason);
    }

    private void publishRegistrationResult(Long userId, ActivityRegistrationStatusDTO status) {
        if (status == null) {
            return;
        }
        ActivityRegistrationPushDTO push = new ActivityRegistrationPushDTO();
        push.setEvent("activity_register_result");
        push.setPayload(status);
        activityRegistrationSessionRegistry.push(userId, push);
    }

    private ActivityRegistrationStatusDTO buildPendingConfirmStatus(ActivityRegistration registration, Activity activity) {
        ActivityRegistrationStatusDTO status = new ActivityRegistrationStatusDTO();
        status.setActivityId(registration.getActivityId());
        status.setUserId(registration.getUserId());
        status.setRequestId(registration.getRequestId());
        status.setStatus(REGISTRATION_STATUS_PENDING_CONFIRM);
        status.setConfirmTime(registration.getConfirmTime());
        status.setMessage("报名确认中，请稍候");
        return status;
    }

    private ActivityRegistrationStatusDTO buildRegistrationStatus(ActivityRegistration registration, ActivityVoucher voucher, Activity activity) {
        ActivityRegistrationStatusDTO status = new ActivityRegistrationStatusDTO();
        if (registration != null) {
            status.setActivityId(registration.getActivityId());
            status.setUserId(registration.getUserId());
            status.setRequestId(registration.getRequestId());
            status.setStatus(mapRegistrationStatus(activity, registration.getStatus()));
            status.setFailReason(registration.getFailReason());
            status.setConfirmTime(registration.getConfirmTime());
            status.setMessage(buildRegistrationMessage(activity, registration.getStatus(), registration.getFailReason()));
        }
        if (voucher != null) {
            status.setVoucherId(voucher.getId());
            status.setVoucherDisplayCode(voucher.getDisplayCode());
            status.setVoucherStatus(voucher.getStatus());
            status.setVoucherIssuedTime(voucher.getIssuedTime());
            status.setVoucherCheckedInTime(voucher.getCheckedInTime());
        }
        return status;
    }

    private ActivityRegistrationStatusDTO resolveRegistrationStatus(Long activityId, Long userId) {
        ActivityUserStateCache redisState = getUserStateFromRedis(activityId, userId);
        if (redisState != null) {
            return buildRegistrationStatus(redisState, activityId, userId);
        }
        Activity activity = getById(activityId);
        ActivityRegistration registration = activityRegistrationMapper.selectOne(new QueryWrapper<ActivityRegistration>()
                .eq("activity_id", activityId)
                .eq("user_id", userId));
        if (registration == null) {
            ActivityRegistrationStatusDTO status = new ActivityRegistrationStatusDTO();
            status.setActivityId(activityId);
            status.setUserId(userId);
            status.setStatus(REGISTRATION_STATUS_NONE);
            status.setMessage("未报名");
            return status;
        }
        ActivityVoucher voucher = registration.getVoucherId() == null ? null : activityVoucherMapper.selectById(registration.getVoucherId());
        ActivityUserStateCache state = buildUserState(registration, voucher, activity);
        cacheUserRegistrationState(activityId, userId, state);
        return buildRegistrationStatus(registration, voucher, activity);
    }

    private ActivityRegistrationStatusDTO buildRegistrationStatus(ActivityUserStateCache state, Long activityId, Long userId) {
        ActivityRegistrationStatusDTO status = new ActivityRegistrationStatusDTO();
        status.setActivityId(activityId);
        status.setUserId(userId);
        status.setRequestId(state.getRequestId());
        status.setStatus(state.getStatus());
        status.setMessage(state.getMessage());
        status.setFailReason(state.getFailReason());
        status.setVoucherId(state.getVoucherId());
        status.setVoucherDisplayCode(state.getVoucherDisplayCode());
        status.setVoucherStatus(state.getVoucherStatus());
        status.setConfirmTime(state.getConfirmTime());
        status.setVoucherIssuedTime(state.getVoucherIssuedTime());
        status.setVoucherCheckedInTime(state.getVoucherCheckedInTime());
        return status;
    }

    private void cachePendingRegistrationStatus(Long activityId, Long userId, String requestId, boolean reviewMode) {
        ActivityUserStateCache state = new ActivityUserStateCache();
        state.setStatus(reviewMode ? REGISTRATION_STATUS_PENDING_REVIEW : REGISTRATION_STATUS_PENDING_CONFIRM);
        state.setRequestId(requestId);
        state.setMessage(reviewMode ? "报名申请已提交，等待主办方审核" : "报名确认中，请稍候");
        state.setRegistered(false);
        cacheUserRegistrationState(activityId, userId, state);
    }

    private void cacheFinalRegistrationStatus(Long activityId, Long userId, ActivityRegistrationStatusDTO dto) {
        ActivityUserStateCache state = new ActivityUserStateCache();
        state.setStatus(dto.getStatus());
        state.setRequestId(dto.getRequestId());
        state.setMessage(dto.getMessage());
        state.setFailReason(dto.getFailReason());
        state.setVoucherId(dto.getVoucherId());
        state.setVoucherDisplayCode(dto.getVoucherDisplayCode());
        state.setVoucherStatus(dto.getVoucherStatus());
        state.setConfirmTime(dto.getConfirmTime());
        state.setVoucherIssuedTime(dto.getVoucherIssuedTime());
        state.setVoucherCheckedInTime(dto.getVoucherCheckedInTime());
        state.setRegistered(REGISTRATION_STATUS_SUCCESS.equals(dto.getStatus())
                || REGISTRATION_STATUS_CANCEL_PENDING.equals(dto.getStatus()));
        state.setCheckedIn(VOUCHER_STATUS_CHECKED_IN.equals(dto.getVoucherStatus()));
        cacheUserRegistrationState(activityId, userId, state);
    }

    private void cacheCanceledRegistrationStatus(Long activityId, Long userId, String requestId, String message) {
        ActivityUserStateCache state = new ActivityUserStateCache();
        state.setStatus(REGISTRATION_STATUS_CANCELED);
        state.setRequestId(requestId);
        state.setMessage(message);
        state.setRegistered(false);
        cacheUserRegistrationState(activityId, userId, state);
    }

    private void cacheCancelPendingRegistrationStatus(Long activityId, Long userId, String requestId, String message) {
        ActivityUserStateCache state = new ActivityUserStateCache();
        state.setStatus(REGISTRATION_STATUS_CANCEL_PENDING);
        state.setRequestId(requestId);
        state.setMessage(message);
        state.setRegistered(true);
        cacheUserRegistrationState(activityId, userId, state);
    }

    private ActivityUserStateCache buildUserState(ActivityRegistration registration, ActivityVoucher voucher, Activity activity) {
        ActivityUserStateCache state = new ActivityUserStateCache();
        if (registration == null) {
            state.setStatus(REGISTRATION_STATUS_NONE);
            state.setMessage("未报名");
            state.setRegistered(false);
            state.setCheckedIn(false);
            return state;
        }
        state.setStatus(mapRegistrationStatus(activity, registration.getStatus()));
        state.setRequestId(registration.getRequestId());
        state.setMessage(buildRegistrationMessage(activity, registration.getStatus(), registration.getFailReason()));
        state.setFailReason(registration.getFailReason());
        state.setConfirmTime(registration.getConfirmTime());
        state.setRegistered(Objects.equals(registration.getStatus(), REGISTRATION_SUCCESS)
                || Objects.equals(registration.getStatus(), REGISTRATION_CANCEL_PENDING));
        state.setCheckedIn(voucher != null && VOUCHER_STATUS_CHECKED_IN.equals(voucher.getStatus()));
        state.setVoucherId(voucher == null ? null : voucher.getId());
        state.setVoucherDisplayCode(voucher == null ? null : voucher.getDisplayCode());
        state.setVoucherStatus(voucher == null ? null : voucher.getStatus());
        state.setVoucherIssuedTime(voucher == null ? null : voucher.getIssuedTime());
        state.setVoucherCheckedInTime(voucher == null ? null : voucher.getCheckedInTime());
        return state;
    }

    private ActivityUserStateCache getUserStateFromRedis(Long activityId, Long userId) {
        Map<Object, Object> stateMap = stringRedisTemplate.opsForHash().entries(activityUserStateKey(activityId, userId));
        if (stateMap == null || stateMap.isEmpty()) {
            return null;
        }
        ActivityUserStateCache state = new ActivityUserStateCache();
        state.setStatus((String) stateMap.get("status"));
        state.setRequestId((String) stateMap.get("requestId"));
        state.setMessage((String) stateMap.get("message"));
        state.setFailReason((String) stateMap.get("failReason"));
        state.setRegistered(Boolean.valueOf(String.valueOf(stateMap.getOrDefault("registered", "false"))));
        state.setCheckedIn(Boolean.valueOf(String.valueOf(stateMap.getOrDefault("checkedIn", "false"))));
        state.setVoucherId(parseLongValue(stateMap.get("voucherId")));
        state.setVoucherDisplayCode((String) stateMap.get("voucherDisplayCode"));
        state.setVoucherStatus((String) stateMap.get("voucherStatus"));
        state.setConfirmTime(parseDateTimeValue(stateMap.get("confirmTime")));
        state.setVoucherIssuedTime(parseDateTimeValue(stateMap.get("voucherIssuedTime")));
        state.setVoucherCheckedInTime(parseDateTimeValue(stateMap.get("voucherCheckedInTime")));
        return state;
    }

    private void cacheUserRegistrationState(Long activityId, Long userId, ActivityUserStateCache state) {
        if (activityId == null || userId == null || state == null) {
            return;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("status", StrUtil.blankToDefault(state.getStatus(), REGISTRATION_STATUS_NONE));
        payload.put("requestId", StrUtil.blankToDefault(state.getRequestId(), ""));
        payload.put("message", StrUtil.blankToDefault(state.getMessage(), ""));
        payload.put("failReason", StrUtil.blankToDefault(state.getFailReason(), ""));
        payload.put("registered", String.valueOf(Boolean.TRUE.equals(state.getRegistered())));
        payload.put("checkedIn", String.valueOf(Boolean.TRUE.equals(state.getCheckedIn())));
        payload.put("voucherId", state.getVoucherId() == null ? "" : state.getVoucherId().toString());
        payload.put("voucherDisplayCode", StrUtil.blankToDefault(state.getVoucherDisplayCode(), ""));
        payload.put("voucherStatus", StrUtil.blankToDefault(state.getVoucherStatus(), ""));
        payload.put("confirmTime", state.getConfirmTime() == null ? "" : state.getConfirmTime().toString());
        payload.put("voucherIssuedTime", state.getVoucherIssuedTime() == null ? "" : state.getVoucherIssuedTime().toString());
        payload.put("voucherCheckedInTime", state.getVoucherCheckedInTime() == null ? "" : state.getVoucherCheckedInTime().toString());
        stringRedisTemplate.opsForHash().putAll(activityUserStateKey(activityId, userId), payload);
        stringRedisTemplate.expire(activityUserStateKey(activityId, userId), ACTIVITY_USER_REGISTER_STATE_TTL_HOURS, TimeUnit.HOURS);
    }

    private String mapRegistrationStatus(Activity activity, Integer status) {
        if (Objects.equals(status, REGISTRATION_PENDING)) {
            return isAuditRequiredMode(activity) ? REGISTRATION_STATUS_PENDING_REVIEW : REGISTRATION_STATUS_PENDING_CONFIRM;
        }
        if (Objects.equals(status, REGISTRATION_SUCCESS)) {
            return REGISTRATION_STATUS_SUCCESS;
        }
        if (Objects.equals(status, REGISTRATION_FAILED)) {
            return REGISTRATION_STATUS_FAILED;
        }
        if (Objects.equals(status, REGISTRATION_CANCELED)) {
            return REGISTRATION_STATUS_CANCELED;
        }
        if (Objects.equals(status, REGISTRATION_CANCEL_PENDING)) {
            return REGISTRATION_STATUS_CANCEL_PENDING;
        }
        return REGISTRATION_STATUS_NONE;
    }

    private String buildRegistrationMessage(Activity activity, Integer status, String failReason) {
        if (Objects.equals(status, REGISTRATION_PENDING)) {
            return isAuditRequiredMode(activity) ? "报名申请已提交，等待主办方审核" : "报名确认中，请稍候";
        }
        if (Objects.equals(status, REGISTRATION_SUCCESS)) {
            return "报名成功";
        }
        if (Objects.equals(status, REGISTRATION_FAILED)) {
            return StrUtil.isBlank(failReason) ? "报名失败，请稍后重试" : failReason;
        }
        if (Objects.equals(status, REGISTRATION_CANCELED)) {
            return "已退出活动";
        }
        if (Objects.equals(status, REGISTRATION_CANCEL_PENDING)) {
            return "退出申请待审核";
        }
        return "未报名";
    }

    private String readRegistrationStatusText(Activity activity, Integer status) {
        if (Objects.equals(status, REGISTRATION_PENDING)) {
            return isAuditRequiredMode(activity) ? "报名待审核" : "报名确认中";
        }
        if (Objects.equals(status, REGISTRATION_SUCCESS)) {
            return "报名成功";
        }
        if (Objects.equals(status, REGISTRATION_FAILED)) {
            return "报名失败";
        }
        if (Objects.equals(status, REGISTRATION_CANCELED)) {
            return "已取消";
        }
        if (Objects.equals(status, REGISTRATION_CANCEL_PENDING)) {
            return "退出待审核";
        }
        return "未知状态";
    }

    private String resolveRegistrationMode(Activity activity) {
        if (activity == null || StrUtil.isBlank(activity.getRegistrationMode())) {
            return REGISTRATION_MODE_AUDIT_REQUIRED;
        }
        String mode = activity.getRegistrationMode().trim().toUpperCase();
        if (!REGISTRATION_MODE_AUDIT_REQUIRED.equals(mode) && !REGISTRATION_MODE_FIRST_COME_FIRST_SERVED.equals(mode)) {
            return REGISTRATION_MODE_AUDIT_REQUIRED;
        }
        return mode;
    }

    private boolean isAuditRequiredMode(Activity activity) {
        return REGISTRATION_MODE_AUDIT_REQUIRED.equals(resolveRegistrationMode(activity));
    }

    private boolean isFirstComeFirstServedMode(Activity activity) {
        return REGISTRATION_MODE_FIRST_COME_FIRST_SERVED.equals(resolveRegistrationMode(activity));
    }

    private boolean matchesFavoriteKeyword(Activity activity, String keyword) {
        if (activity == null || StrUtil.isBlank(keyword)) {
            return true;
        }
        String normalizedKeyword = StrUtil.trim(keyword).toLowerCase();
        return StrUtil.containsIgnoreCase(activity.getTitle(), normalizedKeyword)
                || StrUtil.containsIgnoreCase(activity.getSummary(), normalizedKeyword)
                || StrUtil.containsIgnoreCase(activity.getContent(), normalizedKeyword)
                || StrUtil.containsIgnoreCase(activity.getActivityFlow(), normalizedKeyword)
                || StrUtil.containsIgnoreCase(activity.getFaq(), normalizedKeyword)
                || StrUtil.containsIgnoreCase(activity.getCategory(), normalizedKeyword)
                || StrUtil.containsIgnoreCase(activity.getDisplayCategory(), normalizedKeyword)
                || (activity.getTags() != null && activity.getTags().stream().anyMatch(tag -> StrUtil.containsIgnoreCase(tag.getName(), normalizedKeyword)))
                || StrUtil.containsIgnoreCase(activity.getOrganizerName(), normalizedKeyword)
                || StrUtil.containsIgnoreCase(activity.getLocation(), normalizedKeyword);
    }

    private Long parseLongValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return StrUtil.isBlank(text) ? null : Long.valueOf(text);
    }

    private Result validateManagedActivityPermission(Long activityId, String forbiddenMessage) {
        Activity activity = getById(activityId);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        return Objects.equals(activity.getCreatorId(), UserHolder.getUser().getId())
                ? null
                : Result.fail(forbiddenMessage);
    }

    private LocalDateTime parseDateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return StrUtil.isBlank(text) ? null : LocalDateTime.parse(text);
    }

    private void evictActivityCache(Long activityId, boolean evictCategoryCache) {
        stringRedisTemplate.delete(CACHE_ACTIVITY_DETAIL_KEY + activityId);
        String listPattern = CACHE_ACTIVITY_LIST_KEY + "*";
        java.util.Set<String> listKeys = stringRedisTemplate.keys(listPattern);
        if (listKeys != null && !listKeys.isEmpty()) {
            stringRedisTemplate.delete(listKeys);
        }
        if (evictCategoryCache) {
            stringRedisTemplate.delete(CACHE_ACTIVITY_CATEGORIES_KEY);
        }
        if (activityId == null) {
            return;
        }
        java.util.Set<String> userStateKeys = stringRedisTemplate.keys(activityUserStatePattern(activityId));
        if (userStateKeys != null && !userStateKeys.isEmpty()) {
            stringRedisTemplate.delete(userStateKeys);
        }
        java.util.Set<String> recordKeys = stringRedisTemplate.keys(CACHE_ACTIVITY_CHECK_IN_RECORDS_KEY + activityId + ":*");
        if (recordKeys != null && !recordKeys.isEmpty()) {
            stringRedisTemplate.delete(recordKeys);
        }
        stringRedisTemplate.delete(CACHE_ACTIVITY_CHECK_IN_STATS_KEY + activityId);
        stringRedisTemplate.delete(CACHE_ACTIVITY_CHECK_IN_DASHBOARD_KEY + activityId);
        stringRedisTemplate.delete(activityMetaKey(activityId));
        stringRedisTemplate.delete(activityStockKey(activityId));
        stringRedisTemplate.delete(activityFrozenKey(activityId));
        stringRedisTemplate.delete(activityRegisterUsersKey(activityId));
    }

    private void evictCheckInCache(Long activityId) {
        if (activityId == null) {
            return;
        }
        stringRedisTemplate.delete(CACHE_ACTIVITY_CHECK_IN_STATS_KEY + activityId);
        stringRedisTemplate.delete(CACHE_ACTIVITY_CHECK_IN_DASHBOARD_KEY + activityId);
        java.util.Set<String> recordKeys = stringRedisTemplate.keys(CACHE_ACTIVITY_CHECK_IN_RECORDS_KEY + activityId + ":*");
        if (recordKeys != null && !recordKeys.isEmpty()) {
            stringRedisTemplate.delete(recordKeys);
        }
    }

    private String activityUserStateKey(Long activityId, Long userId) {
        return ACTIVITY_USER_REGISTER_STATE_KEY + activityId + ":" + userId;
    }

    private String activityUserStatePattern(Long activityId) {
        return ACTIVITY_USER_REGISTER_STATE_KEY + activityId + ":*";
    }

    private String activityCheckInRecordsKey(Long activityId, Integer current, Integer pageSize) {
        return CACHE_ACTIVITY_CHECK_IN_RECORDS_KEY + activityId + ":" + current + ":" + pageSize;
    }

    private String activityMetaKey(Long activityId) {
        return ACTIVITY_META_KEY + activityId;
    }

    private String activityStockKey(Long activityId) {
        return ACTIVITY_STOCK_KEY + activityId;
    }

    private String activityFrozenKey(Long activityId) {
        return ACTIVITY_FROZEN_KEY + activityId;
    }

    private String activityRegisterUsersKey(Long activityId) {
        return ACTIVITY_REGISTER_USERS_KEY + activityId;
    }

    private long toEpoch(LocalDateTime time) {
        return time == null ? 0L : time.toEpochSecond(ZoneOffset.UTC);
    }

    private boolean isRegistrationOpen(Activity activity, LocalDateTime now) {
        if (!isPublicActivityStatus(activity.getStatus())) {
            return false;
        }
        boolean afterStart = activity.getRegistrationStartTime() == null || !now.isBefore(activity.getRegistrationStartTime());
        boolean beforeEnd = activity.getRegistrationEndTime() == null || !now.isAfter(activity.getRegistrationEndTime());
        boolean hasCapacity = activity.getMaxParticipants() == null
                || activity.getRegisteredCount() == null
                || activity.getRegisteredCount() < activity.getMaxParticipants();
        return afterStart && beforeEnd && hasCapacity;
    }

    private boolean isPublicActivityStatus(Integer status) {
        return Objects.equals(status, STATUS_PUBLISHED)
                || Objects.equals(status, STATUS_OFFLINE_PENDING_REVIEW);
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return SystemConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, 20);
    }

    private Result requirePermission(String permissionCode, String failMessage) {
        UserDTO user = UserHolder.getUser();
        if (AuthorizationUtils.hasPermission(user, permissionCode)) {
            return null;
        }
        return Result.fail(failMessage);
    }

    private String validateActivity(Activity activity) {
        if (activity == null) {
            return "活动信息不能为空";
        }
        if (StrUtil.isBlank(activity.getTitle())) {
            return "活动标题不能为空";
        }
        if (StrUtil.isBlank(activity.getCategory())) {
            return "活动分类不能为空";
        }
        if (!ACTIVITY_CATEGORY_OPTIONS.contains(activity.getCategory())) {
            return "活动分类不合法";
        }
        normalizeCustomCategory(activity);
        List<Long> tagIds = normalizeTagIds(activity.getTagIds());
        if (tagIds.size() < MIN_ACTIVITY_TAG_COUNT || tagIds.size() > MAX_ACTIVITY_TAG_COUNT) {
            return "请在当前一级分类下选择1-5个标签";
        }
        Map<Long, ActivityTag> tagMap = queryTagMap(tagIds);
        if (tagMap.size() != tagIds.size()) {
            return "标签包含无效项";
        }
        for (Long tagId : tagIds) {
            ActivityTag tag = tagMap.get(tagId);
            if (tag == null || !Objects.equals(tag.getCategoryName(), activity.getCategory())) {
                return "所选标签必须属于当前一级分类";
            }
        }
        activity.setTagIds(tagIds);
        if (StrUtil.isBlank(activity.getRegistrationMode())) {
            activity.setRegistrationMode(REGISTRATION_MODE_AUDIT_REQUIRED);
        }
        if (!REGISTRATION_MODE_AUDIT_REQUIRED.equals(resolveRegistrationMode(activity))
                && !REGISTRATION_MODE_FIRST_COME_FIRST_SERVED.equals(resolveRegistrationMode(activity))) {
            return "报名模式只能选择审核制或先到先得";
        }
        if (StrUtil.isBlank(activity.getLocation())) {
            return "活动地点不能为空";
        }
        if (StrUtil.length(StrUtil.trim(activity.getContactInfo())) > 255) {
            return "主办方联系方式长度不能超过255个字符";
        }
        if (StrUtil.length(StrUtil.trim(activity.getActivityFlow())) > 5000) {
            return "活动流程长度不能超过5000个字符";
        }
        if (StrUtil.length(StrUtil.trim(activity.getFaq())) > 5000) {
            return "常见问题长度不能超过5000个字符";
        }
        if (StrUtil.isBlank(activity.getImages()) && StrUtil.isBlank(activity.getCoverImage())) {
            return "请至少上传一张活动图片";
        }
        if (activity.getMaxParticipants() == null || activity.getMaxParticipants() < 1) {
            return "报名人数上限必须大于0";
        }
        if (activity.getRegistrationStartTime() == null || activity.getRegistrationEndTime() == null) {
            return "请填写完整的报名时间";
        }
        if (activity.getEventStartTime() == null || activity.getEventEndTime() == null) {
            return "请填写完整的活动时间";
        }
        if (!activity.getRegistrationStartTime().isBefore(activity.getRegistrationEndTime())) {
            return "报名开始时间必须早于报名结束时间";
        }
        if (!activity.getEventStartTime().isBefore(activity.getEventEndTime())) {
            return "活动开始时间必须早于活动结束时间";
        }
        if (activity.getRegistrationEndTime().isAfter(activity.getEventStartTime())) {
            return "报名结束时间不能晚于活动开始时间";
        }
        return null;
    }

    private void normalizeActivityImages(Activity activity) {
        if (activity == null) {
            return;
        }
        List<String> imageList = new ArrayList<>();
        if (StrUtil.isNotBlank(activity.getImages())) {
            for (String item : activity.getImages().split(",")) {
                if (StrUtil.isNotBlank(item)) {
                    imageList.add(item.trim());
                }
            }
        }
        if (imageList.isEmpty() && StrUtil.isNotBlank(activity.getCoverImage())) {
            imageList.add(activity.getCoverImage().trim());
        }
        if (imageList.isEmpty()) {
            activity.setImages(null);
            activity.setCoverImage(null);
            return;
        }
        activity.setImages(String.join(",", imageList));
        activity.setCoverImage(imageList.get(0));
    }

    private void normalizeCustomCategory(Activity activity) {
        if (activity == null) {
            return;
        }
        activity.setCustomCategory(null);
    }

    private static class CachedActivityPage {
        private final List<Activity> records;
        private final Long total;

        private CachedActivityPage(List<Activity> records, Long total) {
            this.records = records;
            this.total = total;
        }

        public List<Activity> getRecords() {
            return records;
        }

        public Long getTotal() {
            return total;
        }
    }

    private static class CachedCheckInRecordPage {
        private final List<ActivityCheckInRecord> records;
        private final Long total;

        private CachedCheckInRecordPage(List<ActivityCheckInRecord> records, Long total) {
            this.records = records;
            this.total = total;
        }

        public List<ActivityCheckInRecord> getRecords() {
            return records;
        }

        public Long getTotal() {
            return total;
        }
    }

    private static class ActivityUserStateCache {
        private Boolean registered;
        private Boolean checkedIn;
        private String status;
        private String message;
        private String requestId;
        private String failReason;
        private Long voucherId;
        private String voucherDisplayCode;
        private String voucherStatus;
        private LocalDateTime confirmTime;
        private LocalDateTime voucherIssuedTime;
        private LocalDateTime voucherCheckedInTime;

        public Boolean getRegistered() {
            return registered;
        }

        public void setRegistered(Boolean registered) {
            this.registered = registered;
        }

        public Boolean getCheckedIn() {
            return checkedIn;
        }

        public void setCheckedIn(Boolean checkedIn) {
            this.checkedIn = checkedIn;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getFailReason() {
            return failReason;
        }

        public void setFailReason(String failReason) {
            this.failReason = failReason;
        }

        public Long getVoucherId() {
            return voucherId;
        }

        public void setVoucherId(Long voucherId) {
            this.voucherId = voucherId;
        }

        public String getVoucherDisplayCode() {
            return voucherDisplayCode;
        }

        public void setVoucherDisplayCode(String voucherDisplayCode) {
            this.voucherDisplayCode = voucherDisplayCode;
        }

        public String getVoucherStatus() {
            return voucherStatus;
        }

        public void setVoucherStatus(String voucherStatus) {
            this.voucherStatus = voucherStatus;
        }

        public LocalDateTime getConfirmTime() {
            return confirmTime;
        }

        public void setConfirmTime(LocalDateTime confirmTime) {
            this.confirmTime = confirmTime;
        }

        public LocalDateTime getVoucherIssuedTime() {
            return voucherIssuedTime;
        }

        public void setVoucherIssuedTime(LocalDateTime voucherIssuedTime) {
            this.voucherIssuedTime = voucherIssuedTime;
        }

        public LocalDateTime getVoucherCheckedInTime() {
            return voucherCheckedInTime;
        }

        public void setVoucherCheckedInTime(LocalDateTime voucherCheckedInTime) {
            this.voucherCheckedInTime = voucherCheckedInTime;
        }
    }
}
