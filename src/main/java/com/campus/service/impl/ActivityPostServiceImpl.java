package com.campus.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.dto.ActivityPostCommentCreateDTO;
import com.campus.dto.ActivityPostCreateDTO;
import com.campus.dto.Result;
import com.campus.dto.UserDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityCheckInRecord;
import com.campus.entity.ActivityPost;
import com.campus.entity.ActivityPostComment;
import com.campus.entity.ActivityPostImage;
import com.campus.entity.ActivityPostLike;
import com.campus.entity.ActivityRegistration;
import com.campus.entity.ActivityTag;
import com.campus.entity.ActivityTagRelation;
import com.campus.entity.User;
import com.campus.mapper.ActivityCheckInRecordMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityPostCommentMapper;
import com.campus.mapper.ActivityPostImageMapper;
import com.campus.mapper.ActivityPostLikeMapper;
import com.campus.mapper.ActivityPostMapper;
import com.campus.mapper.ActivityRegistrationMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.mapper.UserMapper;
import com.campus.service.IActivityPostService;
import com.campus.service.EmbeddingTaskService;
import com.campus.service.INotificationService;
import com.campus.utils.RedisIdWorker;
import com.campus.utils.UserHolder;
import com.campus.vo.ActivityPostCommentVO;
import com.campus.vo.ActivityPostVO;
import com.campus.vo.EligibleActivityVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.campus.utils.RedisConstants.CACHE_DISCOVER_POST_PAGE_KEY;
import static com.campus.utils.RedisConstants.CACHE_DISCOVER_POST_PAGE_TTL;
import static com.campus.utils.RedisConstants.DISCOVER_POST_LIKE_COUNT_KEY;
import static com.campus.utils.RedisConstants.DISCOVER_POST_LIKED_KEY;

@Slf4j
@Service
public class ActivityPostServiceImpl extends ServiceImpl<ActivityPostMapper, ActivityPost> implements IActivityPostService {
    private static final DefaultRedisScript<Long> DISCOVER_POST_LIKE_SCRIPT;

    private static final int ACTIVITY_STATUS_PUBLISHED = 2;
    private static final int POST_STATUS_NORMAL = 1;
    private static final int POST_STATUS_DELETED = 2;
    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int COMMENT_STATUS_DELETED = 2;
    private static final int REGISTRATION_SUCCESS = 1;
    private static final int CHECKED_IN = 1;
    private static final String CHECK_IN_RESULT_SUCCESS = "SUCCESS";
    private static final String LIKE_ACTION = "LIKE";
    private static final String UNLIKE_ACTION = "UNLIKE";

    static {
        DISCOVER_POST_LIKE_SCRIPT = new DefaultRedisScript<>();
        DISCOVER_POST_LIKE_SCRIPT.setLocation(new ClassPathResource("discover_post_like.lua"));
        DISCOVER_POST_LIKE_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ActivityPostImageMapper activityPostImageMapper;

    @Resource
    private ActivityPostLikeMapper activityPostLikeMapper;

    @Resource
    private ActivityPostCommentMapper activityPostCommentMapper;

    @Resource
    private ActivityMapper activityMapper;

    @Resource
    private ActivityRegistrationMapper activityRegistrationMapper;

    @Resource
    private ActivityCheckInRecordMapper activityCheckInRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ActivityTagMapper activityTagMapper;

    @Resource
    private ActivityTagRelationMapper activityTagRelationMapper;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private INotificationService notificationService;

    @Resource
    private EmbeddingTaskService embeddingTaskService;

    @Override
    public Result queryPosts(Integer current, Integer pageSize, Long activityId, Long userId) {
        int pageNo = normalizePage(current);
        int size = normalizePageSize(pageSize, 20);
        PageCacheValue cached = readPageCache(pageNo, size, activityId);
        List<ActivityPost> posts;
        long total;
        if (cached != null) {
            posts = cached.getRecords();
            total = cached.getTotal() == null ? 0L : cached.getTotal();
        } else {
            Page<ActivityPost> page = page(new Page<>(pageNo, size),
                    new LambdaQueryWrapper<ActivityPost>()
                            .eq(ActivityPost::getStatus, POST_STATUS_NORMAL)
                            .eq(activityId != null, ActivityPost::getActivityId, activityId)
                            .orderByDesc(ActivityPost::getCreateTime));
            posts = page.getRecords();
            total = page.getTotal();
            writePageCache(pageNo, size, activityId, posts, total);
        }
        Long effectiveUserId = userId != null ? userId : currentUserIdOrNull();
        List<ActivityPostVO> result = buildPostVOs(posts, effectiveUserId);
        return Result.ok(result, total);
    }

    @Override
    @Transactional
    public Result createPost(ActivityPostCreateDTO dto) {
        UserDTO currentUser = requireUser();
        ValidationPayload payload = validatePostCreateDTO(dto);
        if (!payload.isSuccess()) {
            return Result.fail(payload.getMessage());
        }
        Activity activity = payload.getActivity();
        if (!canPublishForActivity(currentUser.getId(), activity.getId())) {
            return Result.fail("仅已报名成功或已签到的用户可发布该活动动态");
        }
        Long postId = redisIdWorker.nextId("activity-post");
        ActivityPost post = new ActivityPost()
                .setId(postId)
                .setActivityId(activity.getId())
                .setUserId(currentUser.getId())
                .setContent(payload.getContent())
                .setVisibility(1)
                .setStatus(POST_STATUS_NORMAL)
                .setLikeCount(0)
                .setCommentCount(0);
        save(post);
        if (CollUtil.isNotEmpty(payload.getImageUrls())) {
            List<ActivityPostImage> images = new ArrayList<>();
            for (int i = 0; i < payload.getImageUrls().size(); i++) {
                images.add(new ActivityPostImage()
                        .setId(redisIdWorker.nextId("activity-post-image"))
                        .setPostId(postId)
                        .setImageUrl(payload.getImageUrls().get(i))
                        .setSortNo(i));
            }
            images.forEach(activityPostImageMapper::insert);
        }
        invalidatePostPageCache();
        embeddingTaskService.touchUser(currentUser.getId(), "DISCOVER_POST_CREATE");
        return Result.ok(buildPostVOs(Collections.singletonList(post), currentUser.getId()).stream().findFirst().orElse(null));
    }

    @Override
    @Transactional
    public Result deletePost(Long postId) {
        UserDTO currentUser = requireUser();
        ActivityPost post = getById(postId);
        if (post == null || !Objects.equals(post.getStatus(), POST_STATUS_NORMAL)) {
            return Result.fail("动态不存在或已删除");
        }
        if (!Objects.equals(post.getUserId(), currentUser.getId())) {
            return Result.fail("仅作者本人可删除动态");
        }
        boolean updated = update(new LambdaUpdateWrapper<ActivityPost>()
                .eq(ActivityPost::getId, postId)
                .eq(ActivityPost::getUserId, currentUser.getId())
                .eq(ActivityPost::getStatus, POST_STATUS_NORMAL)
                .set(ActivityPost::getStatus, POST_STATUS_DELETED));
        if (!updated) {
            return Result.fail("动态删除失败");
        }
        activityPostCommentMapper.update(null, new LambdaUpdateWrapper<ActivityPostComment>()
                .eq(ActivityPostComment::getPostId, postId)
                .eq(ActivityPostComment::getStatus, COMMENT_STATUS_NORMAL)
                .set(ActivityPostComment::getStatus, COMMENT_STATUS_DELETED));
        invalidatePostPageCache();
        embeddingTaskService.touchUser(currentUser.getId(), "DISCOVER_POST_DELETE");
        return Result.ok();
    }

    @Override
    @Transactional
    public Result likePost(Long postId) {
        UserDTO currentUser = requireUser();
        ActivityPost post = getNormalPost(postId);
        if (post == null) {
            return Result.fail("动态不存在或不可操作");
        }
        ensurePostLikeCache(post);
        Long redisResult = executeLikeScript(postId, currentUser.getId(), LIKE_ACTION);
        if (redisResult == null || redisResult < 0) {
            return Result.fail("点赞操作失败");
        }
        if (redisResult == 0L) {
            return Result.ok();
        }
        ActivityPostLike like = new ActivityPostLike()
                .setId(redisIdWorker.nextId("activity-post-like"))
                .setPostId(postId)
                .setUserId(currentUser.getId());
        boolean persisted = false;
        try {
            activityPostLikeMapper.insert(like);
            persisted = true;
            baseMapper.update(null, new LambdaUpdateWrapper<ActivityPost>()
                    .eq(ActivityPost::getId, postId)
                    .eq(ActivityPost::getStatus, POST_STATUS_NORMAL)
                    .setSql("like_count = like_count + 1"));
        } catch (DuplicateKeyException e) {
            log.debug("重复点赞直接按成功返回 postId={}, userId={}", postId, currentUser.getId());
        } catch (Exception e) {
            executeLikeScript(postId, currentUser.getId(), UNLIKE_ACTION);
            log.warn("点赞落库失败，已回滚 Redis postId={}, userId={}", postId, currentUser.getId(), e);
            return Result.fail("点赞操作失败");
        }
        invalidatePostPageCache();
        if (persisted) {
            notifyPostLiked(post, currentUser);
        }
        embeddingTaskService.touchUser(currentUser.getId(), "DISCOVER_POST_LIKE");
        return Result.ok();
    }

    @Override
    @Transactional
    public Result unlikePost(Long postId) {
        UserDTO currentUser = requireUser();
        ActivityPost post = getNormalPost(postId);
        if (post == null) {
            return Result.fail("动态不存在或不可操作");
        }
        ensurePostLikeCache(post);
        Long redisResult = executeLikeScript(postId, currentUser.getId(), UNLIKE_ACTION);
        if (redisResult == null || redisResult < 0) {
            return Result.fail("取消点赞失败");
        }
        if (redisResult == 0L) {
            return Result.ok();
        }
        int deleted = activityPostLikeMapper.delete(new LambdaQueryWrapper<ActivityPostLike>()
                .eq(ActivityPostLike::getPostId, postId)
                .eq(ActivityPostLike::getUserId, currentUser.getId()));
        try {
            if (deleted > 0) {
                baseMapper.update(null, new LambdaUpdateWrapper<ActivityPost>()
                        .eq(ActivityPost::getId, postId)
                        .eq(ActivityPost::getStatus, POST_STATUS_NORMAL)
                        .apply("like_count > 0")
                        .setSql("like_count = like_count - 1"));
            }
        } catch (Exception e) {
            executeLikeScript(postId, currentUser.getId(), LIKE_ACTION);
            log.warn("取消点赞落库失败，已回滚 Redis postId={}, userId={}", postId, currentUser.getId(), e);
            return Result.fail("取消点赞失败");
        }
        invalidatePostPageCache();
        embeddingTaskService.touchUser(currentUser.getId(), "DISCOVER_POST_UNLIKE");
        return Result.ok();
    }

    @Override
    public Result queryComments(Long postId, Integer current, Integer pageSize) {
        ActivityPost post = getNormalPost(postId);
        if (post == null) {
            return Result.fail("动态不存在或不可查看评论");
        }
        int pageNo = normalizePage(current);
        int size = normalizePageSize(pageSize, 20);
        Page<ActivityPostComment> page = new Page<>(pageNo, size);
        Page<ActivityPostComment> commentPage = activityPostCommentMapper.selectPage(page,
                new LambdaQueryWrapper<ActivityPostComment>()
                        .eq(ActivityPostComment::getPostId, postId)
                        .eq(ActivityPostComment::getStatus, COMMENT_STATUS_NORMAL)
                        .orderByAsc(ActivityPostComment::getCreateTime));
        List<ActivityPostCommentVO> vos = buildCommentVOs(commentPage.getRecords());
        return Result.ok(vos, commentPage.getTotal());
    }

    @Override
    @Transactional
    public Result createComment(Long postId, ActivityPostCommentCreateDTO dto) {
        UserDTO currentUser = requireUser();
        ActivityPost post = getNormalPost(postId);
        if (post == null) {
            return Result.fail("动态不存在或不可评论");
        }
        String content = normalizeCommentContent(dto);
        if (content == null) {
            return Result.fail("评论内容长度需在 1-200 个字符之间");
        }
        ActivityPostComment comment = new ActivityPostComment()
                .setId(redisIdWorker.nextId("activity-post-comment"))
                .setPostId(postId)
                .setUserId(currentUser.getId())
                .setContent(content)
                .setStatus(COMMENT_STATUS_NORMAL);
        activityPostCommentMapper.insert(comment);
        baseMapper.update(null, new LambdaUpdateWrapper<ActivityPost>()
                .eq(ActivityPost::getId, postId)
                .eq(ActivityPost::getStatus, POST_STATUS_NORMAL)
                .setSql("comment_count = comment_count + 1"));
        invalidatePostPageCache();
        notifyPostCommented(post, currentUser, content);
        embeddingTaskService.touchUser(currentUser.getId(), "DISCOVER_POST_COMMENT");
        return Result.ok(buildCommentVOs(Collections.singletonList(comment)).stream().findFirst().orElse(null));
    }

    @Override
    @Transactional
    public Result deleteComment(Long commentId) {
        UserDTO currentUser = requireUser();
        ActivityPostComment comment = activityPostCommentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getStatus(), COMMENT_STATUS_NORMAL)) {
            return Result.fail("评论不存在或已删除");
        }
        if (!Objects.equals(comment.getUserId(), currentUser.getId())) {
            return Result.fail("仅评论作者本人可删除评论");
        }
        boolean updated = activityPostCommentMapper.update(null, new LambdaUpdateWrapper<ActivityPostComment>()
                .eq(ActivityPostComment::getId, commentId)
                .eq(ActivityPostComment::getUserId, currentUser.getId())
                .eq(ActivityPostComment::getStatus, COMMENT_STATUS_NORMAL)
                .set(ActivityPostComment::getStatus, COMMENT_STATUS_DELETED)) > 0;
        if (!updated) {
            return Result.fail("评论删除失败");
        }
        baseMapper.update(null, new LambdaUpdateWrapper<ActivityPost>()
                .eq(ActivityPost::getId, comment.getPostId())
                .apply("comment_count > 0")
                .setSql("comment_count = comment_count - 1"));
        invalidatePostPageCache();
        embeddingTaskService.touchUser(currentUser.getId(), "DISCOVER_POST_COMMENT_DELETE");
        return Result.ok();
    }

    @Override
    public Result queryEligibleActivities() {
        UserDTO currentUser = requireUser();
        List<EligibleActivityVO> result = queryEligibleActivities(currentUser.getId());
        return Result.ok(result, (long) result.size());
    }

    private ValidationPayload validatePostCreateDTO(ActivityPostCreateDTO dto) {
        if (dto == null || dto.getActivityId() == null) {
            return ValidationPayload.fail("动态必须绑定活动");
        }
        String content = StrUtil.trimToEmpty(dto.getContent());
        if (content.length() < 1 || content.length() > 1000) {
            return ValidationPayload.fail("动态内容长度需在 1-1000 个字符之间");
        }
        List<String> imageUrls = normalizeImageUrls(dto.getImageUrls());
        if (imageUrls == null) {
            return ValidationPayload.fail("图片最多 9 张且 URL 不能为空");
        }
        Activity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null || !Objects.equals(activity.getStatus(), ACTIVITY_STATUS_PUBLISHED)) {
            return ValidationPayload.fail("活动不存在或当前不可发布动态");
        }
        return ValidationPayload.success(activity, content, imageUrls);
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return Collections.emptyList();
        }
        if (imageUrls.size() > 9) {
            return null;
        }
        List<String> result = new ArrayList<>(imageUrls.size());
        for (String imageUrl : imageUrls) {
            String value = StrUtil.trimToEmpty(imageUrl);
            if (StrUtil.isBlank(value)) {
                return null;
            }
            result.add(value);
        }
        return result;
    }

    private String normalizeCommentContent(ActivityPostCommentCreateDTO dto) {
        String content = dto == null ? "" : StrUtil.trimToEmpty(dto.getContent());
        if (content.length() < 1 || content.length() > 200) {
            return null;
        }
        return content;
    }

    private ActivityPost getNormalPost(Long postId) {
        if (postId == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<ActivityPost>()
                .eq(ActivityPost::getId, postId)
                .eq(ActivityPost::getStatus, POST_STATUS_NORMAL));
    }

    private List<ActivityPostVO> buildPostVOs(List<ActivityPost> posts, Long currentUserId) {
        if (CollUtil.isEmpty(posts)) {
            return Collections.emptyList();
        }
        List<Long> postIds = posts.stream().map(ActivityPost::getId).collect(Collectors.toList());
        List<Long> userIds = posts.stream().map(ActivityPost::getUserId).distinct().collect(Collectors.toList());
        List<Long> activityIds = posts.stream().map(ActivityPost::getActivityId).distinct().collect(Collectors.toList());

        Map<Long, List<String>> imageMap = activityPostImageMapper.selectList(new LambdaQueryWrapper<ActivityPostImage>()
                        .in(ActivityPostImage::getPostId, postIds)
                        .orderByAsc(ActivityPostImage::getSortNo))
                .stream()
                .collect(Collectors.groupingBy(ActivityPostImage::getPostId,
                        LinkedHashMap::new,
                        Collectors.mapping(ActivityPostImage::getImageUrl, Collectors.toList())));
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, item -> item));
        Map<Long, Activity> activityMap = activityIds.isEmpty() ? Collections.emptyMap() :
                activityMapper.selectBatchIds(activityIds).stream().collect(Collectors.toMap(Activity::getId, item -> item));
        Map<Long, List<String>> activityTagNameMap = queryActivityTagNameMap(activityIds);

        List<ActivityPostVO> result = new ArrayList<>(posts.size());
        for (ActivityPost post : posts) {
            User user = userMap.get(post.getUserId());
            Activity activity = activityMap.get(post.getActivityId());
            ensurePostLikeCache(post);
            ActivityPostVO vo = new ActivityPostVO();
            vo.setId(post.getId());
            vo.setContent(post.getContent());
            vo.setImageUrls(imageMap.getOrDefault(post.getId(), Collections.emptyList()));
            vo.setCreatedAt(post.getCreateTime());
            vo.setUserId(post.getUserId());
            vo.setNickName(user == null ? "校园同学" : user.getNickName());
            vo.setIcon(user == null ? "" : user.getIcon());
            vo.setActivityId(post.getActivityId());
            vo.setActivityTitle(activity == null ? "" : activity.getTitle());
            vo.setActivityCoverImage(activity == null ? "" : activity.getCoverImage());
            vo.setActivityCategory(activity == null ? "" : displayCategory(activity, activityTagNameMap.get(activity.getId())));
            vo.setActivityStartTime(activity == null ? null : activity.getEventStartTime());
            vo.setActivityStartTimeText(activity == null ? "" : buildActivityStartTimeText(activity.getEventStartTime()));
            vo.setActivityStatusText(activity == null ? "" : buildActivityStatusText(activity));
            vo.setLikeCount(resolveLikeCount(post));
            vo.setCommentCount(defaultCount(post.getCommentCount()));
            vo.setLiked(Boolean.FALSE);
            result.add(vo);
        }
        fillLikedStatus(result, currentUserId);
        return result;
    }

    private List<ActivityPostCommentVO> buildCommentVOs(List<ActivityPostComment> comments) {
        if (CollUtil.isEmpty(comments)) {
            return Collections.emptyList();
        }
        List<Long> userIds = comments.stream().map(ActivityPostComment::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, item -> item));
        List<ActivityPostCommentVO> result = new ArrayList<>(comments.size());
        for (ActivityPostComment comment : comments) {
            User user = userMap.get(comment.getUserId());
            ActivityPostCommentVO vo = new ActivityPostCommentVO();
            vo.setId(comment.getId());
            vo.setPostId(comment.getPostId());
            vo.setUserId(comment.getUserId());
            vo.setNickName(user == null ? "校园同学" : user.getNickName());
            vo.setIcon(user == null ? "" : user.getIcon());
            vo.setContent(comment.getContent());
            vo.setCreatedAt(comment.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    private void fillLikedStatus(List<ActivityPostVO> posts, Long currentUserId) {
        if (currentUserId == null || CollUtil.isEmpty(posts)) {
            return;
        }
        for (ActivityPostVO post : posts) {
            if (Boolean.TRUE.equals(isPostLiked(post.getId(), currentUserId))) {
                post.setLiked(Boolean.TRUE);
            }
        }
    }

    private Integer resolveLikeCount(ActivityPost post) {
        if (post == null || post.getId() == null) {
            return 0;
        }
        String cached = stringRedisTemplate.opsForValue().get(discoverPostLikeCountKey(post.getId()));
        if (StrUtil.isNotBlank(cached)) {
            try {
                return Integer.parseInt(cached);
            } catch (NumberFormatException ignored) {
                return defaultCount(post.getLikeCount());
            }
        }
        return defaultCount(post.getLikeCount());
    }

    private Boolean isPostLiked(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return Boolean.FALSE;
        }
        Boolean liked = stringRedisTemplate.opsForSet().isMember(discoverPostLikedKey(postId), String.valueOf(userId));
        return Boolean.TRUE.equals(liked);
    }

    private void ensurePostLikeCache(ActivityPost post) {
        if (post == null || post.getId() == null) {
            return;
        }
        String likedKey = discoverPostLikedKey(post.getId());
        String countKey = discoverPostLikeCountKey(post.getId());
        Boolean likedExists = stringRedisTemplate.hasKey(likedKey);
        Boolean countExists = stringRedisTemplate.hasKey(countKey);
        if (Boolean.TRUE.equals(likedExists) && Boolean.TRUE.equals(countExists)) {
            return;
        }
        List<String> userIds = activityPostLikeMapper.selectList(new LambdaQueryWrapper<ActivityPostLike>()
                        .eq(ActivityPostLike::getPostId, post.getId()))
                .stream()
                .map(ActivityPostLike::getUserId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toList());
        if (!userIds.isEmpty()) {
            stringRedisTemplate.opsForSet().add(likedKey, userIds.toArray(new String[0]));
        } else {
            stringRedisTemplate.opsForSet().add(likedKey, "__init__");
            stringRedisTemplate.opsForSet().remove(likedKey, "__init__");
        }
        stringRedisTemplate.opsForValue().set(countKey, String.valueOf(userIds.size()));
    }

    private Long executeLikeScript(Long postId, Long userId, String action) {
        return stringRedisTemplate.execute(
                DISCOVER_POST_LIKE_SCRIPT,
                java.util.Arrays.asList(discoverPostLikedKey(postId), discoverPostLikeCountKey(postId)),
                String.valueOf(userId),
                action
        );
    }

    private String discoverPostLikedKey(Long postId) {
        return DISCOVER_POST_LIKED_KEY + postId;
    }

    private String discoverPostLikeCountKey(Long postId) {
        return DISCOVER_POST_LIKE_COUNT_KEY + postId;
    }

    private void notifyPostLiked(ActivityPost post, UserDTO actor) {
        if (post == null || actor == null || post.getUserId() == null || Objects.equals(post.getUserId(), actor.getId())) {
            return;
        }
        String actorName = StrUtil.blankToDefault(actor.getNickName(), "有同学");
        notificationService.notifyUsers(
                Collections.singletonList(post.getUserId()),
                "你的动态收到了点赞",
                actorName + " 点赞了你的校园圈动态。",
                "DISCOVER_POST_LIKED",
                "DISCOVER_POST",
                post.getId()
        );
    }

    private void notifyPostCommented(ActivityPost post, UserDTO actor, String content) {
        if (post == null || actor == null || post.getUserId() == null || Objects.equals(post.getUserId(), actor.getId())) {
            return;
        }
        String actorName = StrUtil.blankToDefault(actor.getNickName(), "有同学");
        String commentSnippet = StrUtil.maxLength(StrUtil.blankToDefault(content, ""), 30);
        notificationService.notifyUsers(
                Collections.singletonList(post.getUserId()),
                "你的动态收到了评论",
                actorName + " 评论了你的校园圈动态：" + commentSnippet,
                "DISCOVER_POST_COMMENTED",
                "DISCOVER_POST",
                post.getId()
        );
    }

    private boolean canPublishForActivity(Long userId, Long activityId) {
        Long registrationCount = activityRegistrationMapper.selectCount(new LambdaQueryWrapper<ActivityRegistration>()
                .eq(ActivityRegistration::getActivityId, activityId)
                .eq(ActivityRegistration::getUserId, userId)
                .and(wrapper -> wrapper
                        .eq(ActivityRegistration::getStatus, REGISTRATION_SUCCESS)
                        .or()
                        .eq(ActivityRegistration::getCheckInStatus, CHECKED_IN)));
        if (registrationCount != null && registrationCount > 0) {
            return true;
        }
        Long checkInCount = activityCheckInRecordMapper.selectCount(new LambdaQueryWrapper<ActivityCheckInRecord>()
                .eq(ActivityCheckInRecord::getActivityId, activityId)
                .eq(ActivityCheckInRecord::getUserId, userId)
                .eq(ActivityCheckInRecord::getResultStatus, CHECK_IN_RESULT_SUCCESS));
        return checkInCount != null && checkInCount > 0;
    }

    private List<EligibleActivityVO> queryEligibleActivities(Long userId) {
        Set<Long> activityIds = new LinkedHashSet<>();
        activityRegistrationMapper.selectList(new LambdaQueryWrapper<ActivityRegistration>()
                        .eq(ActivityRegistration::getUserId, userId)
                        .and(wrapper -> wrapper
                                .eq(ActivityRegistration::getStatus, REGISTRATION_SUCCESS)
                                .or()
                                .eq(ActivityRegistration::getCheckInStatus, CHECKED_IN)))
                .forEach(item -> activityIds.add(item.getActivityId()));
        activityCheckInRecordMapper.selectList(new LambdaQueryWrapper<ActivityCheckInRecord>()
                        .eq(ActivityCheckInRecord::getUserId, userId)
                        .eq(ActivityCheckInRecord::getResultStatus, CHECK_IN_RESULT_SUCCESS))
                .forEach(item -> activityIds.add(item.getActivityId()));
        if (activityIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Activity> activities = activityMapper.selectBatchIds(activityIds).stream()
                .filter(item -> Objects.equals(item.getStatus(), ACTIVITY_STATUS_PUBLISHED))
                .sorted((a, b) -> {
                    LocalDateTime left = a.getEventStartTime();
                    LocalDateTime right = b.getEventStartTime();
                    if (left == null && right == null) {
                        return 0;
                    }
                    if (left == null) {
                        return 1;
                    }
                    if (right == null) {
                        return -1;
                    }
                    return left.compareTo(right);
                })
                .collect(Collectors.toList());
        Map<Long, List<String>> activityTagNameMap = queryActivityTagNameMap(activities.stream().map(Activity::getId).collect(Collectors.toList()));
        List<EligibleActivityVO> result = new ArrayList<>(activities.size());
        for (Activity activity : activities) {
            EligibleActivityVO vo = new EligibleActivityVO();
            vo.setActivityId(activity.getId());
            vo.setActivityTitle(activity.getTitle());
            vo.setActivityCoverImage(activity.getCoverImage());
            vo.setActivityCategory(displayCategory(activity, activityTagNameMap.get(activity.getId())));
            vo.setEventStartTime(activity.getEventStartTime());
            vo.setEventEndTime(activity.getEventEndTime());
            result.add(vo);
        }
        return result;
    }

    private String displayCategory(Activity activity, List<String> tagNames) {
        if (activity == null) {
            return "";
        }
        if (tagNames != null && !tagNames.isEmpty()) {
            return StrUtil.blankToDefault(activity.getCategory(), "") + " / " + String.join("、", tagNames);
        }
        return StrUtil.blankToDefault(activity.getCategory(), "");
    }

    private Map<Long, List<String>> queryActivityTagNameMap(List<Long> activityIds) {
        if (CollUtil.isEmpty(activityIds)) {
            return Collections.emptyMap();
        }
        List<ActivityTagRelation> relations = activityTagRelationMapper.selectList(new LambdaQueryWrapper<ActivityTagRelation>()
                .in(ActivityTagRelation::getActivityId, activityIds)
                .orderByAsc(ActivityTagRelation::getId));
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyMap();
        }
        List<Long> tagIds = relations.stream().map(ActivityTagRelation::getTagId).distinct().collect(Collectors.toList());
        Map<Long, String> tagNameMap = activityTagMapper.selectList(new LambdaQueryWrapper<ActivityTag>()
                        .in(ActivityTag::getId, tagIds)
                        .eq(ActivityTag::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(ActivityTag::getId, ActivityTag::getName, (a, b) -> a));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (ActivityTagRelation relation : relations) {
            String name = tagNameMap.get(relation.getTagId());
            if (StrUtil.isBlank(name)) {
                continue;
            }
            result.computeIfAbsent(relation.getActivityId(), key -> new ArrayList<>()).add(name);
        }
        return result;
    }

    private String buildActivityStatusText(Activity activity) {
        if (activity == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = activity.getEventStartTime();
        LocalDateTime endTime = activity.getEventEndTime();
        LocalDateTime registrationStartTime = activity.getRegistrationStartTime();
        LocalDateTime registrationEndTime = activity.getRegistrationEndTime();
        if (endTime != null && now.isAfter(endTime)) {
            return "已结束";
        }
        if (startTime != null && !now.isBefore(startTime)) {
            return "已开始";
        }
        if (registrationStartTime != null && registrationEndTime != null
                && !now.isBefore(registrationStartTime)
                && !now.isAfter(registrationEndTime)) {
            return "开放报名";
        }
        if (registrationStartTime != null && now.isBefore(registrationStartTime)) {
            return "未开放报名";
        }
        return "报名已截止";
    }

    private String buildActivityStartTimeText(LocalDateTime startTime) {
        if (startTime == null) {
            return "";
        }
        return "开始时间 " + startTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private int defaultCount(Integer count) {
        return count == null || count < 0 ? 0 : count;
    }

    private int normalizePage(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }

    private int normalizePageSize(Integer pageSize, int maxSize) {
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return Math.min(size, maxSize);
    }

    private UserDTO requireUser() {
        return UserHolder.getUser();
    }

    private Long currentUserIdOrNull() {
        UserDTO user = UserHolder.getUser();
        return user == null ? null : user.getId();
    }

    private String buildPageCacheKey(Integer current, Integer pageSize, Long activityId) {
        String raw = "current=" + current + "&pageSize=" + pageSize + "&activityId=" + Objects.toString(activityId, "");
        return CACHE_DISCOVER_POST_PAGE_KEY + DigestUtil.md5Hex(raw);
    }

    private PageCacheValue readPageCache(Integer current, Integer pageSize, Long activityId) {
        String key = buildPageCacheKey(current, pageSize, activityId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return JSONUtil.toBean(json, PageCacheValue.class);
    }

    private void writePageCache(Integer current, Integer pageSize, Long activityId, List<ActivityPost> posts, Long total) {
        PageCacheValue cacheValue = new PageCacheValue();
        cacheValue.setRecords(posts);
        cacheValue.setTotal(total);
        stringRedisTemplate.opsForValue().set(buildPageCacheKey(current, pageSize, activityId),
                JSONUtil.toJsonStr(cacheValue),
                CACHE_DISCOVER_POST_PAGE_TTL,
                TimeUnit.MINUTES);
    }

    private void invalidatePostPageCache() {
        Set<String> keys = stringRedisTemplate.keys(CACHE_DISCOVER_POST_PAGE_KEY + "*");
        if (CollUtil.isNotEmpty(keys)) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Data
    private static class ValidationPayload {
        private boolean success;
        private String message;
        private Activity activity;
        private String content;
        private List<String> imageUrls;

        static ValidationPayload fail(String message) {
            ValidationPayload payload = new ValidationPayload();
            payload.setMessage(message);
            return payload;
        }

        static ValidationPayload success(Activity activity, String content, List<String> imageUrls) {
            ValidationPayload payload = new ValidationPayload();
            payload.setSuccess(true);
            payload.setActivity(activity);
            payload.setContent(content);
            payload.setImageUrls(imageUrls);
            return payload;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    private static class PageCacheValue {
        private List<ActivityPost> records = Collections.emptyList();
        private Long total = 0L;
    }
}
