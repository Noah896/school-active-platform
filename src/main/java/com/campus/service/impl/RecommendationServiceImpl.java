package com.campus.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.dto.RecommendationActivityDTO;
import com.campus.dto.RecommendationPageDTO;
import com.campus.dto.Result;
import com.campus.dto.UserDTO;
import com.campus.dto.ActivityVectorRecallDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityCategory;
import com.campus.entity.ActivityPost;
import com.campus.entity.ActivityRegistration;
import com.campus.entity.ActivityTag;
import com.campus.entity.ActivityTagRelation;
import com.campus.entity.UserPreferenceTag;
import com.campus.entity.UserProfileEmbedding;
import com.campus.mapper.ActivityCategoryMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityPostMapper;
import com.campus.mapper.ActivityRegistrationMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.mapper.UserPreferenceTagMapper;
import com.campus.mapper.UserProfileEmbeddingMapper;
import com.campus.service.RecommendationService;
import com.campus.service.EmbeddingTaskService;
import com.campus.utils.ActivityCategoryConstants;
import com.campus.utils.UserHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.campus.utils.RedisConstants.RECOMMENDATION_FALLBACK_HOT_KEY;
import static com.campus.utils.RedisConstants.RECOMMENDATION_GLOBAL_VERSION_KEY;
import static com.campus.utils.RedisConstants.RECOMMENDATION_USER_KEY;
import static com.campus.utils.RedisConstants.RECOMMENDATION_USER_TTL_MINUTES;
import static com.campus.utils.RedisConstants.RECOMMENDATION_USER_VERSION_KEY;

@Slf4j
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final int ACTIVITY_STATUS_PUBLISHED = 2;
    private static final int REGISTRATION_PENDING = 0;
    private static final int REGISTRATION_SUCCESS = 1;
    private static final int REGISTRATION_CANCEL_PENDING = 4;
    private static final int POST_STATUS_NORMAL = 1;
    private static final int DEFAULT_CURRENT = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final long FALLBACK_CACHE_SIZE_MULTIPLIER = 5L;

    @Resource
    private ActivityMapper activityMapper;
    @Resource
    private ActivityTagRelationMapper activityTagRelationMapper;
    @Resource
    private ActivityTagMapper activityTagMapper;
    @Resource
    private ActivityCategoryMapper activityCategoryMapper;
    @Resource
    private UserPreferenceTagMapper userPreferenceTagMapper;
    @Resource
    private UserProfileEmbeddingMapper userProfileEmbeddingMapper;
    @Resource
    private ActivityRegistrationMapper activityRegistrationMapper;
    @Resource
    private ActivityPostMapper activityPostMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RecommendationScoreCalculator scoreCalculator;
    @Resource
    private RecommendationReasonBuilder reasonBuilder;
    @Resource
    private EmbeddingTaskService embeddingTaskService;
    @Resource
    private EmbeddingSearchService embeddingSearchService;

    @Override
    public Result queryRecommendations(Integer current, Integer pageSize) {
        int pageNo = normalizeCurrent(current);
        int pageLimit = normalizePageSize(pageSize);
        UserDTO user = UserHolder.getUser();
        Long userId = user == null || user.getId() == null ? 0L : user.getId();
        embeddingTaskService.ensureUserProfileEmbedding(userId);
        long version = getRecommendationVersion(userId);
        long globalVersion = getGlobalRecommendationVersion();
        String cacheKey = buildUserRecommendationCacheKey(userId, version, globalVersion, pageNo, pageLimit);
        RecommendationPageDTO cachedPage = readRecommendationCache(cacheKey);
        if (cachedPage != null) {
            RecommendationPageDTO safePage = filterCachedPage(cachedPage, userId);
            return Result.ok(safePage);
        }

        RecommendationPageDTO page = buildRecommendationPage(userId, pageNo, pageLimit);
        writeRecommendationCache(cacheKey, page);
        return Result.ok(page);
    }

    private RecommendationPageDTO buildRecommendationPage(Long userId, int current, int pageSize) {
        List<UserPreferenceTag> preferences = queryUserPreferences(userId);
        UserProfileEmbedding profileEmbedding = queryCurrentUserEmbedding(userId);
        float[] userVector = parseEmbeddingVector(profileEmbedding);
        List<ActivityVectorRecallDTO> vectorHits = userVector == null
                ? Collections.emptyList()
                : embeddingSearchService.searchSimilarActivities(userVector, 50, 0.60D);
        boolean hasPreferences = CollUtil.isNotEmpty(preferences);
        boolean hasVectorRecall = CollUtil.isNotEmpty(vectorHits);
        RecommendationContext context = buildRecommendationContext(userId, preferences, vectorHits);
        List<RecommendationCandidate> rankedCandidates = rankCandidates(context);
        RecommendationPageDTO page = paginateCandidates(rankedCandidates, current, pageSize, false, null);
        if ((!hasPreferences && !hasVectorRecall) || CollUtil.isEmpty(page.getRecords())) {
            RecommendationPageDTO fallbackPage = loadFallbackPage(userId, current, pageSize);
            fallbackPage.setFallback(Boolean.TRUE);
            fallbackPage.setMessage(hasPreferences
                    ? "当前没有更匹配的活动，先为你展示热门和即将开始的活动"
                    : hasVectorRecall
                    ? "当前没有更匹配的活动，先为你展示热门和即将开始的活动"
                    : "你还没有设置偏好标签，先为你展示热门和即将开始的活动");
            return fallbackPage;
        }
        page.setFallback(Boolean.FALSE);
        page.setMessage(null);
        return page;
    }

    private RecommendationContext buildRecommendationContext(Long userId,
                                                             List<UserPreferenceTag> preferences,
                                                             List<ActivityVectorRecallDTO> vectorHits) {
        RecommendationContext context = new RecommendationContext();
        context.setUserId(userId);
        context.setPreferences(preferences == null ? Collections.emptyList() : preferences);
        context.setExcludedActivityIds(queryExcludedActivityIds(userId));

        List<Long> preferenceTagIds = context.getPreferences().stream()
                .map(UserPreferenceTag::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ActivityTag> preferenceTagMap = queryTagMap(preferenceTagIds);
        context.setPreferenceTagMap(preferenceTagMap);
        context.setPreferenceCategoryCounts(buildPreferenceCategoryCounts(preferenceTagMap.values()));
        context.setPrimaryCategories(resolvePrimaryCategories(context.getPreferenceCategoryCounts()));
        Map<Long, Double> vectorScoreMap = vectorHits == null ? Collections.emptyMap() : vectorHits.stream()
                .filter(hit -> hit != null && hit.getActivityId() != null)
                .collect(Collectors.toMap(ActivityVectorRecallDTO::getActivityId, ActivityVectorRecallDTO::getSimilarity,
                        (a, b) -> a, LinkedHashMap::new));
        context.setVectorScoreMap(vectorScoreMap);
        context.setVectorRecallIds(new LinkedHashSet<>(vectorScoreMap.keySet()));

        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        candidateIds.addAll(limitIds(recallByMatchedTags(preferenceTagIds), 50));
        candidateIds.addAll(limitIds(recallByPreferredCategories(context.getPreferenceCategoryCounts().keySet()), 50));
        candidateIds.addAll(limitIds(recallHotActivities(), 50));
        candidateIds.addAll(limitIds(recallUpcomingActivities(7), 50));
        candidateIds.addAll(limitIds(recallLatestActivities(), 50));
        candidateIds.addAll(vectorScoreMap.keySet());

        if (!candidateIds.isEmpty()) {
            candidateIds.removeAll(context.getExcludedActivityIds());
        }
        context.setCandidateIds(candidateIds);
        context.setActivities(queryActivities(new ArrayList<>(candidateIds)));
        context.setActivityTagMap(queryActivityTagMap(context.getActivities().keySet()));
        context.setInteractionMap(queryInteractionMap(context.getActivities().keySet()));
        return context;
    }

    private RecommendationPageDTO loadFallbackPage(Long userId, int current, int pageSize) {
        long globalVersion = getGlobalRecommendationVersion();
        String cacheKey = RECOMMENDATION_FALLBACK_HOT_KEY + pageSize + ":v:" + globalVersion;
        RecommendationPageDTO cachedPage = readRecommendationCache(cacheKey);
        if (cachedPage != null) {
            return filterCachedPage(cachedPage, userId, current, pageSize);
        }

        RecommendationContext context = new RecommendationContext();
        context.setUserId(userId);
        context.setExcludedActivityIds(queryExcludedActivityIds(userId));
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        candidateIds.addAll(recallHotActivities());
        candidateIds.addAll(recallUpcomingActivities(7));
        context.setActivities(queryActivities(candidateIds.stream()
                .filter(id -> !context.getExcludedActivityIds().contains(id))
                .collect(Collectors.toList())));
        context.setActivityTagMap(queryActivityTagMap(context.getActivities().keySet()));
        context.setInteractionMap(queryInteractionMap(context.getActivities().keySet()));
        List<RecommendationCandidate> rankedCandidates = rankCandidates(context);
        RecommendationPageDTO cachedFallback = paginateCandidates(
                rankedCandidates,
                1,
                Math.max((int) (pageSize * FALLBACK_CACHE_SIZE_MULTIPLIER), pageSize),
                true,
                null
        );
        writeRecommendationCache(cacheKey, cachedFallback);
        return filterCachedPage(cachedFallback, userId, current, pageSize);
    }

    private RecommendationPageDTO filterCachedPage(RecommendationPageDTO page, Long userId) {
        return filterCachedPage(page, userId, 1, page == null || page.getRecords() == null ? DEFAULT_PAGE_SIZE : page.getRecords().size());
    }

    private RecommendationPageDTO filterCachedPage(RecommendationPageDTO page, Long userId, int current, int pageSize) {
        RecommendationPageDTO safePage = new RecommendationPageDTO();
        if (page == null || CollUtil.isEmpty(page.getRecords())) {
            safePage.setRecords(Collections.emptyList());
            safePage.setTotal(0L);
            safePage.setFallback(page == null ? Boolean.FALSE : page.getFallback());
            safePage.setMessage(page == null ? null : page.getMessage());
            return safePage;
        }
        List<Long> activityIds = page.getRecords().stream()
                .map(RecommendationActivityDTO::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Set<Long> excludedActivityIds = queryExcludedActivityIds(userId);
        Map<Long, Activity> activityMap = queryActivities(activityIds);
        List<RecommendationActivityDTO> validRecords = page.getRecords().stream()
                .filter(item -> {
                    Activity activity = activityMap.get(item.getActivityId());
                    return activity != null && isRecommendableActivity(activity) && !excludedActivityIds.contains(activity.getId());
                })
                .collect(Collectors.toList());
        int fromIndex = Math.min((current - 1) * pageSize, validRecords.size());
        int toIndex = Math.min(fromIndex + pageSize, validRecords.size());
        safePage.setRecords(validRecords.subList(fromIndex, toIndex));
        safePage.setTotal((long) validRecords.size());
        safePage.setFallback(page.getFallback());
        safePage.setMessage(page.getMessage());
        return safePage;
    }

    private RecommendationPageDTO paginateCandidates(List<RecommendationCandidate> rankedCandidates,
                                                     int current,
                                                     int pageSize,
                                                     boolean fallback,
                                                     String message) {
        RecommendationPageDTO page = new RecommendationPageDTO();
        if (CollUtil.isEmpty(rankedCandidates)) {
            page.setRecords(Collections.emptyList());
            page.setTotal(0L);
            page.setFallback(fallback);
            page.setMessage(message);
            return page;
        }
        int fromIndex = Math.min((current - 1) * pageSize, rankedCandidates.size());
        int toIndex = Math.min(fromIndex + pageSize, rankedCandidates.size());
        page.setRecords(rankedCandidates.subList(fromIndex, toIndex).stream()
                .map(this::toRecommendationDTO)
                .collect(Collectors.toList()));
        page.setTotal((long) rankedCandidates.size());
        page.setFallback(fallback);
        page.setMessage(message);
        return page;
    }

    private List<RecommendationCandidate> rankCandidates(RecommendationContext context) {
        if (context == null || context.getActivities().isEmpty()) {
            return Collections.emptyList();
        }
        List<RecommendationCandidate> candidates = new ArrayList<>();
        int maxRegisteredCount = context.getActivities().values().stream()
                .map(Activity::getRegisteredCount)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        long maxInteractionCount = context.getInteractionMap().values().stream()
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        int preferenceTagCount = context.getPreferenceTagMap().size();
        LocalDateTime now = LocalDateTime.now();

        for (Long activityId : context.getCandidateIds()) {
            Activity activity = context.getActivities().get(activityId);
            if (activity == null || !isRecommendableActivity(activity) || context.getExcludedActivityIds().contains(activityId)) {
                continue;
            }
            List<ActivityTag> activityTags = context.getActivityTagMap().getOrDefault(activityId, Collections.emptyList());
            List<String> tagNames = activityTags.stream()
                    .map(ActivityTag::getName)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
            List<ActivityTag> matchedTags = activityTags.stream()
                    .filter(tag -> context.getPreferenceTagMap().containsKey(tag.getId()))
                    .collect(Collectors.toList());
            boolean matchedPrimaryCategory = context.getPrimaryCategories().contains(activity.getCategory());
            boolean ruleMatched = !matchedTags.isEmpty() || matchedPrimaryCategory;
            Double vectorScore = context.getVectorScoreMap().get(activityId);
            boolean vectorMatched = vectorScore != null && vectorScore > 0D;
            long interactionCount = context.getInteractionMap().getOrDefault(activityId, 0L);
            long daysUntilStart = resolveDaysUntilStart(activity, now);

            RecommendationCandidate candidate = new RecommendationCandidate();
            candidate.setActivity(activity);
            candidate.setTagNames(tagNames);
            candidate.setMatchedTagNames(matchedTags.stream().map(ActivityTag::getName).collect(Collectors.toList()));
            candidate.setMatchedPrimaryCategory(matchedPrimaryCategory);
            candidate.setRuleMatched(ruleMatched);
            candidate.setVectorMatched(vectorMatched);
            candidate.setVectorScore(vectorScore);
            candidate.setInteractionCount(interactionCount);
            candidate.setHot(maxRegisteredCount > 0 && Objects.equals(activity.getRegisteredCount(), maxRegisteredCount));
            candidate.setUpcomingSoon(daysUntilStart <= 7L);
            candidate.setTagScore(scoreCalculator.calculateTagScore(matchedTags.size(), preferenceTagCount));
            candidate.setCategoryScore(scoreCalculator.calculateCategoryScore(matchedPrimaryCategory));
            candidate.setHeatScore(scoreCalculator.calculateHeatScore(
                    activity.getRegisteredCount() == null ? 0 : activity.getRegisteredCount(),
                    maxRegisteredCount
            ));
            candidate.setTimeScore(scoreCalculator.calculateTimeScore(daysUntilStart));
            candidate.setInteractionScore(scoreCalculator.calculateInteractionScore(interactionCount, maxInteractionCount));
            candidate.setScore(scoreCalculator.calculate(candidate));
            candidate.setReason(reasonBuilder.build(candidate));
            candidates.add(candidate);
        }

        candidates.sort(Comparator
                .comparing(RecommendationCandidate::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> candidate.getActivity().getEventStartTime(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(candidate -> candidate.getActivity().getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder())));
        return candidates;
    }

    private RecommendationActivityDTO toRecommendationDTO(RecommendationCandidate candidate) {
        RecommendationActivityDTO dto = new RecommendationActivityDTO();
        Activity activity = candidate.getActivity();
        dto.setActivityId(activity.getId());
        dto.setTitle(activity.getTitle());
        dto.setCoverImage(activity.getCoverImage());
        dto.setCategoryName(activity.getCategory());
        dto.setDisplayCategory(buildDisplayCategory(activity.getCategory(), candidate.getTagNames()));
        dto.setTags(candidate.getTagNames());
        dto.setStartTime(activity.getEventStartTime());
        dto.setLocation(activity.getLocation());
        dto.setScore(candidate.getScore());
        dto.setReason(candidate.getReason());
        return dto;
    }

    private String buildDisplayCategory(String category, List<String> tags) {
        if (StrUtil.isBlank(category)) {
            return CollUtil.isEmpty(tags) ? "未分类" : String.join(" / ", tags);
        }
        return CollUtil.isEmpty(tags) ? category : category + " / " + String.join("、", tags);
    }

    private boolean isRecommendableActivity(Activity activity) {
        if (activity == null || !Objects.equals(activity.getStatus(), ACTIVITY_STATUS_PUBLISHED)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getEventEndTime() != null && !activity.getEventEndTime().isAfter(now)) {
            return false;
        }
        if (activity.getRegistrationEndTime() != null && activity.getRegistrationEndTime().isBefore(now)) {
            return false;
        }
        Integer maxParticipants = activity.getMaxParticipants();
        Integer registeredCount = activity.getRegisteredCount();
        return maxParticipants == null || registeredCount == null || registeredCount < maxParticipants;
    }

    private long resolveDaysUntilStart(Activity activity, LocalDateTime now) {
        if (activity == null || activity.getEventStartTime() == null) {
            return 365L;
        }
        long days = Duration.between(now, activity.getEventStartTime()).toDays();
        return Math.max(0L, days);
    }

    private Set<Long> recallByMatchedTags(List<Long> preferenceTagIds) {
        if (CollUtil.isEmpty(preferenceTagIds)) {
            return Collections.emptySet();
        }
        return activityTagRelationMapper.selectList(new QueryWrapper<ActivityTagRelation>()
                        .in("tag_id", preferenceTagIds)
                        .orderByAsc("id"))
                .stream()
                .map(ActivityTagRelation::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> recallByPreferredCategories(Collection<String> categoryNames) {
        if (CollUtil.isEmpty(categoryNames)) {
            return Collections.emptySet();
        }
        return activityMapper.selectList(new QueryWrapper<Activity>()
                        .select("id")
                        .in("category", categoryNames)
                        .eq("status", ACTIVITY_STATUS_PUBLISHED)
                        .orderByAsc("event_start_time")
                        .orderByDesc("create_time"))
                .stream()
                .map(Activity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> recallHotActivities() {
        return activityMapper.selectList(new QueryWrapper<Activity>()
                        .select("id")
                        .eq("status", ACTIVITY_STATUS_PUBLISHED)
                        .orderByDesc("registered_count")
                        .orderByAsc("event_start_time")
                        .orderByDesc("create_time"))
                .stream()
                .map(Activity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> recallUpcomingActivities(int days) {
        LocalDateTime now = LocalDateTime.now();
        return activityMapper.selectList(new QueryWrapper<Activity>()
                        .select("id")
                        .eq("status", ACTIVITY_STATUS_PUBLISHED)
                        .ge("event_start_time", now)
                        .le("event_start_time", now.plusDays(days))
                        .orderByAsc("event_start_time")
                        .orderByDesc("create_time"))
                .stream()
                .map(Activity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> recallLatestActivities() {
        return activityMapper.selectList(new QueryWrapper<Activity>()
                        .select("id")
                        .eq("status", ACTIVITY_STATUS_PUBLISHED)
                        .orderByDesc("create_time"))
                .stream()
                .map(Activity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, Activity> queryActivities(List<Long> activityIds) {
        if (CollUtil.isEmpty(activityIds)) {
            return Collections.emptyMap();
        }
        return activityMapper.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, List<ActivityTag>> queryActivityTagMap(Collection<Long> activityIds) {
        if (CollUtil.isEmpty(activityIds)) {
            return Collections.emptyMap();
        }
        List<ActivityTagRelation> relations = activityTagRelationMapper.selectList(new QueryWrapper<ActivityTagRelation>()
                .in("activity_id", activityIds)
                .orderByAsc("id"));
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> tagIdsByActivity = new LinkedHashMap<>();
        for (ActivityTagRelation relation : relations) {
            tagIdsByActivity.computeIfAbsent(relation.getActivityId(), key -> new ArrayList<>()).add(relation.getTagId());
        }
        Map<Long, ActivityTag> tagMap = queryTagMap(tagIdsByActivity.values().stream()
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList()));
        Map<Long, List<ActivityTag>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Long>> entry : tagIdsByActivity.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        return result;
    }

    private Map<Long, ActivityTag> queryTagMap(List<Long> tagIds) {
        if (CollUtil.isEmpty(tagIds)) {
            return Collections.emptyMap();
        }
        List<ActivityTag> tags = activityTagMapper.selectList(new QueryWrapper<ActivityTag>()
                .in("id", tagIds)
                .eq("status", 1)
                .orderByAsc("sort_no")
                .orderByAsc("id"));
        if (CollUtil.isEmpty(tags)) {
            return Collections.emptyMap();
        }
        Map<Long, String> categoryNameMap = queryCategoryNameMap(tags.stream()
                .map(ActivityTag::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        tags.forEach(tag -> tag.setCategoryName(categoryNameMap.get(tag.getCategoryId())));
        return tags.stream().collect(Collectors.toMap(ActivityTag::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, String> queryCategoryNameMap(List<Long> categoryIds) {
        if (CollUtil.isEmpty(categoryIds)) {
            return Collections.emptyMap();
        }
        return activityCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ActivityCategory::getId, ActivityCategory::getName, (a, b) -> a));
    }

    private List<UserPreferenceTag> queryUserPreferences(Long userId) {
        if (userId == null || userId <= 0L) {
            return Collections.emptyList();
        }
        return userPreferenceTagMapper.selectList(new QueryWrapper<UserPreferenceTag>()
                .eq("user_id", userId)
                .eq("source", ActivityCategoryConstants.PREFERENCE_SOURCE_MANUAL)
                .orderByAsc("id"));
    }

    private UserProfileEmbedding queryCurrentUserEmbedding(Long userId) {
        if (userId == null || userId <= 0L) {
            return null;
        }
        return userProfileEmbeddingMapper.selectOne(new QueryWrapper<UserProfileEmbedding>()
                .eq("user_id", userId)
                .eq("status", 1)
                .orderByDesc("updated_at")
                .last("limit 1"));
    }

    private float[] parseEmbeddingVector(UserProfileEmbedding embedding) {
        if (embedding == null || StrUtil.isBlank(embedding.getEmbeddingVector())) {
            return null;
        }
        try {
            List<Number> values = JSONUtil.parseArray(embedding.getEmbeddingVector()).toList(Number.class);
            if (CollUtil.isEmpty(values)) {
                return null;
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }
            return vector;
        } catch (Exception e) {
            log.warn("解析用户画像向量失败 userId={}", embedding.getUserId(), e);
            return null;
        }
    }

    private Map<String, Integer> buildPreferenceCategoryCounts(Collection<ActivityTag> preferenceTags) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (CollUtil.isEmpty(preferenceTags)) {
            return counts;
        }
        for (ActivityTag tag : preferenceTags) {
            if (StrUtil.isBlank(tag.getCategoryName())) {
                continue;
            }
            counts.merge(tag.getCategoryName(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private Set<String> resolvePrimaryCategories(Map<String, Integer> categoryCounts) {
        if (categoryCounts.isEmpty()) {
            return Collections.emptySet();
        }
        int max = categoryCounts.values().stream().max(Integer::compareTo).orElse(0);
        return categoryCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Set<Long> queryExcludedActivityIds(Long userId) {
        if (userId == null || userId <= 0L) {
            return Collections.emptySet();
        }
        return activityRegistrationMapper.selectList(new QueryWrapper<ActivityRegistration>()
                        .select("activity_id")
                        .eq("user_id", userId)
                        .in("status", REGISTRATION_PENDING, REGISTRATION_SUCCESS, REGISTRATION_CANCEL_PENDING))
                .stream()
                .map(ActivityRegistration::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private LinkedHashSet<Long> limitIds(Collection<Long> ids, int limit) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (CollUtil.isEmpty(ids) || limit <= 0) {
            return result;
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            result.add(id);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Map<Long, Long> queryInteractionMap(Collection<Long> activityIds) {
        if (CollUtil.isEmpty(activityIds)) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = activityPostMapper.selectMaps(new QueryWrapper<ActivityPost>()
                .select("activity_id", "COALESCE(SUM(like_count + comment_count), 0) AS interaction_count")
                .in("activity_id", activityIds)
                .eq("status", POST_STATUS_NORMAL)
                .groupBy("activity_id"));
        if (CollUtil.isEmpty(rows)) {
            return Collections.emptyMap();
        }
        Map<Long, Long> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object activityId = row.get("activity_id");
            Object interactionCount = row.get("interaction_count");
            if (activityId == null || interactionCount == null) {
                continue;
            }
            result.put(Long.valueOf(String.valueOf(activityId)), Long.valueOf(String.valueOf(interactionCount)));
        }
        return result;
    }

    private RecommendationPageDTO readRecommendationCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return JSONUtil.toBean(json, RecommendationPageDTO.class);
        } catch (DataAccessException e) {
            log.warn("读取推荐缓存失败，降级实时计算 key={}", key, e);
            return null;
        }
    }

    private void writeRecommendationCache(String key, RecommendationPageDTO page) {
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(page), RECOMMENDATION_USER_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (DataAccessException e) {
            log.warn("写入推荐缓存失败 key={}", key, e);
        }
    }

    private long getRecommendationVersion(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(buildRecommendationVersionKey(userId));
            return StrUtil.isBlank(value) ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取推荐版本失败 userId={}", userId, e);
            return 0L;
        }
    }

    private String buildUserRecommendationCacheKey(Long userId, long version, long globalVersion, int current, int pageSize) {
        return RECOMMENDATION_USER_KEY + userId + ":v:" + version + ":gv:" + globalVersion + ":page:" + current + ":size:" + pageSize;
    }

    private String buildRecommendationVersionKey(Long userId) {
        return RECOMMENDATION_USER_VERSION_KEY + userId + ":version";
    }

    private long getGlobalRecommendationVersion() {
        try {
            String value = stringRedisTemplate.opsForValue().get(RECOMMENDATION_GLOBAL_VERSION_KEY);
            return StrUtil.isBlank(value) ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取全局推荐版本失败", e);
            return 0L;
        }
    }

    private int normalizeCurrent(Integer current) {
        return current == null || current < 1 ? DEFAULT_CURRENT : current;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    @Data
    static class RecommendationContext {
        private Long userId;
        private List<UserPreferenceTag> preferences = Collections.emptyList();
        private Map<Long, ActivityTag> preferenceTagMap = Collections.emptyMap();
        private Map<String, Integer> preferenceCategoryCounts = Collections.emptyMap();
        private Set<String> primaryCategories = Collections.emptySet();
        private Set<Long> excludedActivityIds = Collections.emptySet();
        private Set<Long> vectorRecallIds = Collections.emptySet();
        private Map<Long, Double> vectorScoreMap = Collections.emptyMap();
        private LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        private Map<Long, Activity> activities = Collections.emptyMap();
        private Map<Long, List<ActivityTag>> activityTagMap = Collections.emptyMap();
        private Map<Long, Long> interactionMap = Collections.emptyMap();
    }

    @Data
    public static class RecommendationCandidate {
        private Activity activity;
        private List<String> tagNames = Collections.emptyList();
        private List<String> matchedTagNames = Collections.emptyList();
        private Boolean matchedPrimaryCategory = Boolean.FALSE;
        private Boolean ruleMatched = Boolean.FALSE;
        private Boolean vectorMatched = Boolean.FALSE;
        private Double vectorScore = 0D;
        private Boolean hot = Boolean.FALSE;
        private Boolean upcomingSoon = Boolean.FALSE;
        private Long interactionCount = 0L;
        private Double tagScore = 0D;
        private Double categoryScore = 0D;
        private Double heatScore = 0D;
        private Double timeScore = 0D;
        private Double interactionScore = 0D;
        private Double score = 0D;
        private String reason;
    }
}
