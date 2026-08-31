package com.campus.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.campus.config.EmbeddingProperties;
import com.campus.dto.ActivityVectorRecallDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityCheckInRecord;
import com.campus.entity.ActivityFavorite;
import com.campus.entity.ActivityPost;
import com.campus.entity.ActivityPostComment;
import com.campus.entity.ActivityPostLike;
import com.campus.entity.ActivityRegistration;
import com.campus.entity.ActivityTag;
import com.campus.entity.ActivityTagRelation;
import com.campus.entity.EmbeddingTask;
import com.campus.entity.User;
import com.campus.entity.UserPreferenceTag;
import com.campus.entity.UserProfileEmbedding;
import com.campus.mapper.ActivityCheckInRecordMapper;
import com.campus.mapper.ActivityFavoriteMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityPostCommentMapper;
import com.campus.mapper.ActivityPostLikeMapper;
import com.campus.mapper.ActivityPostMapper;
import com.campus.mapper.ActivityRegistrationMapper;
import com.campus.mapper.ActivityCategoryMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.mapper.EmbeddingTaskMapper;
import com.campus.mapper.UserMapper;
import com.campus.mapper.UserPreferenceTagMapper;
import com.campus.mapper.UserProfileEmbeddingMapper;
import com.campus.service.EmbeddingTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmbeddingTaskServiceImpl implements EmbeddingTaskService {

    private static final String BIZ_ACTIVITY = "ACTIVITY";
    private static final String BIZ_USER_PROFILE = "USER_PROFILE";
    private static final int TASK_PENDING = 0;
    private static final int TASK_RUNNING = 1;
    private static final int TASK_SUCCESS = 2;
    private static final int TASK_FAILED = 3;
    private static final int PROFILE_VERSION = 1;
    private static final int PROFILE_STATUS_VALID = 1;
    private static final int ACTIVITY_STATUS_PUBLISHED = 2;
    private static final int REGISTRATION_SUCCESS = 1;
    private static final int CHECKED_IN = 1;
    private static final int POST_STATUS_NORMAL = 1;
    private static final int POST_STATUS_DELETED = 2;
    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int LIKE_WEIGHT = 2;
    private static final int COMMENT_WEIGHT = 3;
    private static final int POST_WEIGHT = 4;
    private static final int FAVORITE_WEIGHT = 4;
    private static final int REGISTRATION_WEIGHT = 5;
    private static final int CHECK_IN_WEIGHT = 8;

    @Resource
    private EmbeddingProperties embeddingProperties;

    @Resource
    private EmbeddingGateway embeddingGateway;

    @Resource
    private EmbeddingSearchService embeddingSearchService;

    @Resource
    private EmbeddingTaskMapper embeddingTaskMapper;

    @Resource
    private UserProfileEmbeddingMapper userProfileEmbeddingMapper;

    @Resource
    private ActivityMapper activityMapper;

    @Resource
    private ActivityTagRelationMapper activityTagRelationMapper;

    @Resource
    private ActivityTagMapper activityTagMapper;

    @Resource
    private ActivityCategoryMapper activityCategoryMapper;

    @Resource
    private ActivityRegistrationMapper activityRegistrationMapper;

    @Resource
    private ActivityFavoriteMapper activityFavoriteMapper;

    @Resource
    private ActivityPostMapper activityPostMapper;

    @Resource
    private ActivityPostLikeMapper activityPostLikeMapper;

    @Resource
    private ActivityPostCommentMapper activityPostCommentMapper;

    @Resource
    private ActivityCheckInRecordMapper activityCheckInRecordMapper;

    @Resource
    private UserPreferenceTagMapper userPreferenceTagMapper;

    @Resource
    private UserMapper userMapper;

    @PostConstruct
    public void initBackfill() {
        ensureActivityBackfill();
    }

    @Override
    public void touchActivity(Long activityId, String trigger) {
        if (activityId == null) {
            return;
        }
        upsertTask(BIZ_ACTIVITY, activityId, trigger);
    }

    @Override
    public void touchUser(Long userId, String trigger) {
        if (userId == null) {
            return;
        }
        upsertTask(BIZ_USER_PROFILE, userId, trigger);
    }

    @Override
    @Scheduled(cron = "${campus.embedding.compensation-cron:0 */2 * * * ?}")
    public void processPendingTasks() {
        if (!embeddingGateway.isAvailable()) {
            return;
        }
        int batchSize = Math.max(1, embeddingProperties.getTaskBatchSize());
        List<EmbeddingTask> tasks = embeddingTaskMapper.selectList(new QueryWrapper<EmbeddingTask>()
                .in("task_status", TASK_PENDING, TASK_FAILED)
                .lt("retry_count", embeddingProperties.getTaskRetryLimit())
                .orderByAsc("id")
                .last("limit " + batchSize));
        if (CollUtil.isEmpty(tasks)) {
            return;
        }
        for (EmbeddingTask task : tasks) {
            if (!claimTask(task)) {
                continue;
            }
            try {
                if (BIZ_ACTIVITY.equals(task.getBizType())) {
                    processActivityTask(task);
                } else if (BIZ_USER_PROFILE.equals(task.getBizType())) {
                    processUserTask(task);
                } else {
                    markTaskFailed(task, "未知任务类型");
                }
            } catch (Exception e) {
                log.warn("处理 embedding 任务失败 bizType={}, bizId={}", task.getBizType(), task.getBizId(), e);
                markTaskFailed(task, StrUtil.blankToDefault(e.getMessage(), "embedding 处理失败"));
            }
        }
    }

    @Override
    public void ensureActivityBackfill() {
        if (!embeddingGateway.isAvailable()) {
            return;
        }
        List<Long> activityIds = activityMapper.selectList(new QueryWrapper<Activity>()
                        .select("id")
                        .eq("status", ACTIVITY_STATUS_PUBLISHED)
                        .orderByAsc("id"))
                .stream()
                .map(Activity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        for (Long activityId : activityIds) {
            upsertTask(BIZ_ACTIVITY, activityId, "BOOTSTRAP");
        }
    }

    @Override
    public void ensureUserProfileEmbedding(Long userId) {
        if (userId == null) {
            return;
        }
        if (!embeddingGateway.isAvailable()) {
            return;
        }
        UserProfileEmbedding current = findUserProfileEmbedding(userId);
        if (current != null && current.getEmbeddingVector() != null) {
            return;
        }
        processUserProfileNow(userId);
    }

    private void processActivityTask(EmbeddingTask task) {
        Activity activity = activityMapper.selectById(task.getBizId());
        if (activity == null) {
            embeddingSearchService.deleteActivityEmbedding(task.getBizId());
            markTaskSuccess(task);
            return;
        }
        String embeddingText = buildActivityEmbeddingText(activity);
        float[] vector = embeddingGateway.embed(embeddingText);
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("活动 embedding 生成失败");
        }
        embeddingSearchService.upsertActivityEmbedding(activity.getId(), embeddingText, vector, embeddingGateway.modelName());
        markTaskSuccess(task);
    }

    private void processUserTask(EmbeddingTask task) {
        processUserProfileNow(task.getBizId());
        markTaskSuccess(task);
    }

    private void processUserProfileNow(Long userId) {
        UserProfileData profileData = buildUserProfileData(userId);
        String profileText = profileData.profileText();
        float[] vector = embeddingGateway.embed(profileText);
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("用户画像 embedding 生成失败");
        }
        persistUserProfileEmbedding(userId, profileText, vector);
    }

    private void persistUserProfileEmbedding(Long userId, String profileText, float[] vector) {
        UserProfileEmbedding current = findUserProfileEmbedding(userId);
        UserProfileEmbedding entity = current == null ? new UserProfileEmbedding() : current;
        if (entity.getId() == null) {
            entity.setUserId(userId);
            entity.setVersion(PROFILE_VERSION);
            entity.setBehaviorWindowDays(embeddingProperties.getBehaviorWindowDays());
            entity.setStatus(PROFILE_STATUS_VALID);
            entity.setModelName(embeddingGateway.modelName());
        }
        entity.setProfileText(profileText);
        entity.setEmbeddingVector(JSONUtil.toJsonStr(toVectorList(vector)));
        entity.setBehaviorWindowDays(embeddingProperties.getBehaviorWindowDays());
        entity.setStatus(PROFILE_STATUS_VALID);
        entity.setModelName(embeddingGateway.modelName());
        if (entity.getId() == null) {
            userProfileEmbeddingMapper.insert(entity);
        } else {
            userProfileEmbeddingMapper.updateById(entity);
        }
    }

    private UserProfileEmbedding findUserProfileEmbedding(Long userId) {
        return userProfileEmbeddingMapper.selectOne(new QueryWrapper<UserProfileEmbedding>()
                .eq("user_id", userId)
                .eq("model_name", embeddingGateway.modelName())
                .eq("version", PROFILE_VERSION)
                .eq("status", PROFILE_STATUS_VALID)
                .orderByDesc("updated_at")
                .last("limit 1"));
    }

    private UserProfileData buildUserProfileData(Long userId) {
        List<UserPreferenceTag> preferenceTags = queryManualPreferenceTags(userId);
        Map<String, Integer> tagCounts = new LinkedHashMap<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        List<String> preferenceTagNames = preferenceTags.stream()
                .map(UserPreferenceTag::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .map(tagId -> queryTagMap(Collections.singletonList(tagId)).get(tagId))
                .filter(Objects::nonNull)
                .map(ActivityTag::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        accumulateTagCounts(tagCounts, preferenceTags.stream()
                .map(UserPreferenceTag::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()), 5);
        List<Long> registrationActivityIds = queryActivityIdsFromRegistrations(userId);
        List<Long> favoriteActivityIds = queryActivityIdsFromFavorites(userId);
        List<Long> postActivityIds = queryActivityIdsFromPosts(userId);
        List<Long> postLikeActivityIds = queryActivityIdsFromPostLikes(userId);
        List<Long> postCommentActivityIds = queryActivityIdsFromPostComments(userId);
        List<Long> checkInActivityIds = queryActivityIdsFromCheckIns(userId);

        accumulateActivitySignal(tagCounts, categoryCounts, registrationActivityIds, REGISTRATION_WEIGHT);
        accumulateActivitySignal(tagCounts, categoryCounts, favoriteActivityIds, FAVORITE_WEIGHT);
        accumulateActivitySignal(tagCounts, categoryCounts, postActivityIds, POST_WEIGHT);
        accumulateActivitySignal(tagCounts, categoryCounts, postLikeActivityIds, LIKE_WEIGHT);
        accumulateActivitySignal(tagCounts, categoryCounts, postCommentActivityIds, COMMENT_WEIGHT);
        accumulateActivitySignal(tagCounts, categoryCounts, checkInActivityIds, CHECK_IN_WEIGHT);

        String summary = buildSummary(preferenceTagNames, tagCounts, categoryCounts);
        String profileText = buildProfileText(preferenceTagNames, registrationActivityIds, favoriteActivityIds,
                postActivityIds, postLikeActivityIds, postCommentActivityIds, checkInActivityIds, tagCounts, categoryCounts, summary);
        return new UserProfileData(profileText);
    }

    private String buildProfileText(List<String> preferenceTagNames,
                                    List<Long> registrationActivityIds,
                                    List<Long> favoriteActivityIds,
                                    List<Long> postActivityIds,
                                    List<Long> postLikeActivityIds,
                                    List<Long> postCommentActivityIds,
                                    List<Long> checkInActivityIds,
                                    Map<String, Integer> tagCounts,
                                    Map<String, Integer> categoryCounts,
                                    String summary) {
        StringBuilder builder = new StringBuilder();
        if (CollUtil.isNotEmpty(preferenceTagNames)) {
            builder.append("用户主动选择的兴趣标签：").append(String.join("、", preferenceTagNames)).append("。");
        }
        builder.append("用户近期强行为：");
        builder.append("报名").append(registrationActivityIds.size()).append("个活动，");
        builder.append("收藏").append(favoriteActivityIds.size()).append("个活动，");
        builder.append("签到").append(checkInActivityIds.size()).append("个活动。");
        builder.append("用户近期弱行为：");
        builder.append("发布动态").append(postActivityIds.size()).append("次，");
        builder.append("点赞动态").append(postLikeActivityIds.size()).append("次，");
        builder.append("评论动态").append(postCommentActivityIds.size()).append("次。");
        if (!tagCounts.isEmpty()) {
            builder.append("聚合后的Top偏好标签：").append(joinTopEntries(tagCounts, 5)).append("。");
        }
        if (!categoryCounts.isEmpty()) {
            builder.append("聚合后的Top偏好分类：").append(joinTopEntries(categoryCounts, 3)).append("。");
        }
        builder.append("用户兴趣总结：").append(summary);
        return builder.toString();
    }

    private String buildSummary(List<String> preferenceTagNames,
                                Map<String, Integer> tagCounts,
                                Map<String, Integer> categoryCounts) {
        if (!tagCounts.isEmpty()) {
            return "该用户更关注" + joinTopKeys(tagCounts, 4) + "类校园活动。";
        }
        if (!categoryCounts.isEmpty()) {
            return "该用户更关注" + joinTopKeys(categoryCounts, 3) + "类校园活动。";
        }
        if (CollUtil.isNotEmpty(preferenceTagNames)) {
            return "该用户更关注" + String.join("、", preferenceTagNames) + "类校园活动。";
        }
        return "该用户当前没有明显偏好，优先推荐校园通用活动。";
    }

    private void accumulateActivitySignal(Map<String, Integer> tagCounts,
                                          Map<String, Integer> categoryCounts,
                                          List<Long> activityIds,
                                          int weight) {
        if (CollUtil.isEmpty(activityIds)) {
            return;
        }
        Map<Long, List<ActivityTag>> activityTagMap = queryActivityTagMap(activityIds);
        for (Long activityId : activityIds) {
            List<ActivityTag> tags = activityTagMap.getOrDefault(activityId, Collections.emptyList());
            for (ActivityTag tag : tags) {
                if (StrUtil.isNotBlank(tag.getName())) {
                    tagCounts.merge(tag.getName(), weight, Integer::sum);
                }
                if (StrUtil.isNotBlank(tag.getCategoryName())) {
                    categoryCounts.merge(tag.getCategoryName(), weight, Integer::sum);
                }
            }
        }
    }

    private void accumulateTagCounts(Map<String, Integer> tagCounts, List<Long> tagIds, int weight) {
        if (CollUtil.isEmpty(tagIds)) {
            return;
        }
        Map<Long, ActivityTag> tagMap = queryTagMap(tagIds);
        for (Long tagId : tagIds) {
            ActivityTag tag = tagMap.get(tagId);
            if (tag != null && StrUtil.isNotBlank(tag.getName())) {
                tagCounts.merge(tag.getName(), weight, Integer::sum);
            }
        }
    }

    private List<UserPreferenceTag> queryManualPreferenceTags(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return userPreferenceTagMapper.selectList(new QueryWrapper<UserPreferenceTag>()
                .eq("user_id", userId)
                .eq("source", "MANUAL")
                .orderByAsc("id"));
    }

    private List<Long> queryActivityIdsFromRegistrations(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LocalDateTime lowerBound = LocalDateTime.now().minusDays(embeddingProperties.getBehaviorWindowDays());
        return activityRegistrationMapper.selectList(new QueryWrapper<ActivityRegistration>()
                        .select("activity_id")
                        .eq("user_id", userId)
                        .eq("status", REGISTRATION_SUCCESS)
                        .ge("update_time", lowerBound)
                        .orderByDesc("update_time"))
                .stream()
                .map(ActivityRegistration::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> queryActivityIdsFromFavorites(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LocalDateTime lowerBound = LocalDateTime.now().minusDays(embeddingProperties.getBehaviorWindowDays());
        return activityFavoriteMapper.selectList(new QueryWrapper<ActivityFavorite>()
                        .select("activity_id")
                        .eq("user_id", userId)
                        .ge("create_time", lowerBound)
                        .orderByDesc("create_time"))
                .stream()
                .map(ActivityFavorite::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> queryActivityIdsFromPosts(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LocalDateTime lowerBound = LocalDateTime.now().minusDays(embeddingProperties.getBehaviorWindowDays());
        return activityPostMapper.selectList(new QueryWrapper<ActivityPost>()
                        .select("activity_id")
                        .eq("user_id", userId)
                        .eq("status", POST_STATUS_NORMAL)
                        .ge("create_time", lowerBound)
                        .orderByDesc("create_time"))
                .stream()
                .map(ActivityPost::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> queryActivityIdsFromPostLikes(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LocalDateTime lowerBound = LocalDateTime.now().minusDays(embeddingProperties.getBehaviorWindowDays());
        List<Long> postIds = activityPostLikeMapper.selectList(new QueryWrapper<ActivityPostLike>()
                        .select("post_id")
                        .eq("user_id", userId)
                        .ge("create_time", lowerBound)
                        .orderByDesc("create_time"))
                .stream()
                .map(ActivityPostLike::getPostId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyList();
        }
        return activityPostMapper.selectBatchIds(postIds).stream()
                .map(ActivityPost::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> queryActivityIdsFromPostComments(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LocalDateTime lowerBound = LocalDateTime.now().minusDays(embeddingProperties.getBehaviorWindowDays());
        List<Long> postIds = activityPostCommentMapper.selectList(new QueryWrapper<ActivityPostComment>()
                        .select("post_id")
                        .eq("user_id", userId)
                        .eq("status", COMMENT_STATUS_NORMAL)
                        .ge("create_time", lowerBound)
                        .orderByDesc("create_time"))
                .stream()
                .map(ActivityPostComment::getPostId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyList();
        }
        return activityPostMapper.selectBatchIds(postIds).stream()
                .map(ActivityPost::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> queryActivityIdsFromCheckIns(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LocalDateTime lowerBound = LocalDateTime.now().minusDays(embeddingProperties.getBehaviorWindowDays());
        return activityCheckInRecordMapper.selectList(new QueryWrapper<ActivityCheckInRecord>()
                        .select("activity_id")
                        .eq("user_id", userId)
                        .ge("create_time", lowerBound)
                        .orderByDesc("create_time"))
                .stream()
                .map(ActivityCheckInRecord::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<Long, List<ActivityTag>> queryActivityTagMap(List<Long> activityIds) {
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
        return activityCategoryMapper.selectBatchIds(categoryIds)
                .stream()
                .collect(Collectors.toMap(com.campus.entity.ActivityCategory::getId,
                        item -> StrUtil.blankToDefault(item.getName(), ""), (a, b) -> a));
    }

    private void upsertTask(String bizType, Long bizId, String trigger) {
        EmbeddingTask existing = embeddingTaskMapper.selectOne(new QueryWrapper<EmbeddingTask>()
                .eq("biz_type", bizType)
                .eq("biz_id", bizId)
                .last("limit 1"));
        if (existing == null) {
            EmbeddingTask task = new EmbeddingTask()
                    .setBizType(bizType)
                    .setBizId(bizId)
                    .setTaskStatus(TASK_PENDING)
                    .setRetryCount(0)
                    .setErrorMessage(null);
            embeddingTaskMapper.insert(task);
            return;
        }
        EmbeddingTask update = new EmbeddingTask();
        update.setId(existing.getId());
        update.setTaskStatus(TASK_PENDING);
        update.setErrorMessage(null);
        if (!Objects.equals(existing.getTaskStatus(), TASK_RUNNING)) {
            update.setRetryCount(0);
        }
        embeddingTaskMapper.updateById(update);
    }

    private boolean claimTask(EmbeddingTask task) {
        if (task == null || task.getId() == null) {
            return false;
        }
        return embeddingTaskMapper.update(null, new UpdateWrapper<EmbeddingTask>()
                .eq("id", task.getId())
                .in("task_status", TASK_PENDING, TASK_FAILED)
                .lt("retry_count", embeddingProperties.getTaskRetryLimit())
                .set("task_status", TASK_RUNNING)
                .set("error_message", null)) > 0;
    }

    private void markTaskSuccess(EmbeddingTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        EmbeddingTask update = new EmbeddingTask();
        update.setId(task.getId());
        update.setTaskStatus(TASK_SUCCESS);
        update.setErrorMessage(null);
        embeddingTaskMapper.updateById(update);
    }

    private void markTaskFailed(EmbeddingTask task, String errorMessage) {
        if (task == null || task.getId() == null) {
            return;
        }
        EmbeddingTask update = new EmbeddingTask();
        update.setId(task.getId());
        update.setTaskStatus(TASK_FAILED);
        update.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        update.setErrorMessage(StrUtil.maxLength(StrUtil.blankToDefault(errorMessage, "embedding 处理失败"), 500));
        embeddingTaskMapper.updateById(update);
    }

    private String buildActivityEmbeddingText(Activity activity) {
        List<String> parts = new ArrayList<>();
        parts.add("活动标题：" + StrUtil.blankToDefault(activity.getTitle(), ""));
        parts.add("活动简介：" + StrUtil.blankToDefault(activity.getSummary(), ""));
        parts.add("活动正文：" + StrUtil.blankToDefault(activity.getContent(), ""));
        parts.add("活动分类：" + StrUtil.blankToDefault(activity.getCategory(), ""));
        parts.add("活动地点：" + StrUtil.blankToDefault(activity.getLocation(), ""));
        parts.add("主办方：" + StrUtil.blankToDefault(activity.getOrganizerName(), ""));
        List<String> tagNames = queryActivityTagMap(Collections.singletonList(activity.getId()))
                .getOrDefault(activity.getId(), Collections.emptyList())
                .stream()
                .map(ActivityTag::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        if (!tagNames.isEmpty()) {
            parts.add("活动标签：" + String.join("、", tagNames));
        }
        return String.join("。", parts);
    }

    private List<Float> toVectorList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float item : vector) {
            result.add(item);
        }
        return result;
    }

    private String joinTopEntries(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> entry.getKey() + "：" + entry.getValue())
                .collect(Collectors.joining("、"));
    }

    private String joinTopKeys(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining("、"));
    }

    private record UserProfileData(String profileText) {
    }
}
