package com.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.dto.ActivityPostCommentCreateDTO;
import com.campus.dto.ActivityPostCreateDTO;
import com.campus.dto.Result;
import com.campus.dto.UserDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityPost;
import com.campus.entity.ActivityPostLike;
import com.campus.entity.ActivityPostImage;
import com.campus.entity.User;
import com.campus.mapper.ActivityCheckInRecordMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityPostCommentMapper;
import com.campus.mapper.ActivityPostImageMapper;
import com.campus.mapper.ActivityPostLikeMapper;
import com.campus.mapper.ActivityPostMapper;
import com.campus.mapper.ActivityRegistrationMapper;
import com.campus.mapper.UserMapper;
import com.campus.service.INotificationService;
import com.campus.utils.RedisIdWorker;
import com.campus.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityPostServiceImplTest {

    @Mock
    private ActivityPostMapper activityPostMapper;
    @Mock
    private ActivityPostImageMapper activityPostImageMapper;
    @Mock
    private ActivityPostLikeMapper activityPostLikeMapper;
    @Mock
    private ActivityPostCommentMapper activityPostCommentMapper;
    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityRegistrationMapper activityRegistrationMapper;
    @Mock
    private ActivityCheckInRecordMapper activityCheckInRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisIdWorker redisIdWorker;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private INotificationService notificationService;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ActivityPostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ActivityPostServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", activityPostMapper);
        ReflectionTestUtils.setField(service, "activityPostImageMapper", activityPostImageMapper);
        ReflectionTestUtils.setField(service, "activityPostLikeMapper", activityPostLikeMapper);
        ReflectionTestUtils.setField(service, "activityPostCommentMapper", activityPostCommentMapper);
        ReflectionTestUtils.setField(service, "activityMapper", activityMapper);
        ReflectionTestUtils.setField(service, "activityRegistrationMapper", activityRegistrationMapper);
        ReflectionTestUtils.setField(service, "activityCheckInRecordMapper", activityCheckInRecordMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "redisIdWorker", redisIdWorker);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(stringRedisTemplate.keys(any())).thenReturn(Collections.emptySet());
        lenient().when(activityPostLikeMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().doReturn(1L).when(stringRedisTemplate).execute(any(), anyList(), anyString(), anyString());
        lenient().doReturn(Boolean.FALSE).when(setOperations).isMember(anyString(), anyString());

        UserDTO user = new UserDTO();
        user.setId(100L);
        user.setNickName("测试同学");
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldRejectWhenImageCountExceedsNine() {
        ActivityPostCreateDTO dto = new ActivityPostCreateDTO();
        dto.setActivityId(1L);
        dto.setContent("动态内容");
        dto.setImageUrls(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));

        Result result = service.createPost(dto);

        assertFalse(result.getSuccess());
        assertEquals("图片最多 9 张且 URL 不能为空", result.getErrorMsg());
    }

    @Test
    void shouldRejectCreatePostWhenUserNotEligible() {
        Activity activity = publishedActivity();
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(activityRegistrationMapper.selectCount(any())).thenReturn(0L);
        when(activityCheckInRecordMapper.selectCount(any())).thenReturn(0L);

        ActivityPostCreateDTO dto = new ActivityPostCreateDTO();
        dto.setActivityId(1L);
        dto.setContent("报名失败用户不能发");

        Result result = service.createPost(dto);

        assertFalse(result.getSuccess());
        assertEquals("仅已报名成功或已签到的用户可发布该活动动态", result.getErrorMsg());
        verify(activityPostMapper, never()).insert(org.mockito.ArgumentMatchers.<ActivityPost>any());
    }

    @Test
    void shouldCreatePostWhenUserEligible() {
        Activity activity = publishedActivity();
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(activityRegistrationMapper.selectCount(any())).thenReturn(1L);
        when(redisIdWorker.nextId(any())).thenReturn(1000L, 1001L, 1002L);
        doReturn(1).when(activityPostMapper).insert(org.mockito.ArgumentMatchers.<ActivityPost>any());
        doReturn(1).when(activityPostImageMapper).insert(org.mockito.ArgumentMatchers.<ActivityPostImage>any());
        when(activityPostImageMapper.selectList(any())).thenReturn(Arrays.asList(
                new ActivityPostImage().setPostId(1000L).setImageUrl("https://img/1.png").setSortNo(0),
                new ActivityPostImage().setPostId(1000L).setImageUrl("https://img/2.png").setSortNo(1)
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(new User().setId(100L).setNickName("测试同学").setIcon("avatar")));
        when(activityMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(activity));
        ActivityPostCreateDTO dto = new ActivityPostCreateDTO();
        dto.setActivityId(1L);
        dto.setContent("  可以发布  ");
        dto.setImageUrls(Arrays.asList("https://img/1.png", "https://img/2.png"));

        Result result = service.createPost(dto);

        assertTrue(result.getSuccess());
        verify(activityPostMapper).insert(org.mockito.ArgumentMatchers.<ActivityPost>any());
        verify(activityPostImageMapper, times(2)).insert(org.mockito.ArgumentMatchers.<ActivityPostImage>any());
    }

    @Test
    void shouldTreatDuplicateLikeAsSuccessWithoutIncrement() {
        doReturn(new ActivityPost().setId(8L).setStatus(1)).when(activityPostMapper).selectOne(any(), eq(true));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(activityPostLikeMapper)
                .insert(org.mockito.ArgumentMatchers.<com.campus.entity.ActivityPostLike>any());

        Result result = service.likePost(8L);

        assertTrue(result.getSuccess());
        verify(activityPostMapper, never()).update(eq(null), any(Wrapper.class));
        verify(notificationService, never()).notifyUsers(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldAllowUnlikeIdempotently() {
        doReturn(new ActivityPost().setId(9L).setStatus(1)).when(activityPostMapper).selectOne(any(), eq(true));
        when(activityPostLikeMapper.delete(any())).thenReturn(0);

        Result result = service.unlikePost(9L);

        assertTrue(result.getSuccess());
        verify(activityPostMapper, never()).update(eq(null), any(Wrapper.class));
    }

    @Test
    void shouldMarkPostsLikedFromDatabaseRecords() {
        ActivityPost record = new ActivityPost()
                .setId(66L)
                .setActivityId(1L)
                .setUserId(200L)
                .setContent("测试动态")
                .setStatus(1)
                .setLikeCount(1)
                .setCommentCount(0)
                .setCreateTime(LocalDateTime.now());
        Page<ActivityPost> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(record));
        page.setTotal(1);
        when(activityPostMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(activityPostImageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(new User().setId(200L).setNickName("作者")));
        when(activityMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(publishedActivity()));
        when(activityPostLikeMapper.selectList(any())).thenReturn(Collections.singletonList(new ActivityPostLike().setPostId(66L).setUserId(100L)));
        doReturn(Boolean.TRUE).when(setOperations).isMember(anyString(), eq("100"));

        Result result = service.queryPosts(1, 10, null, 100L);

        assertTrue(result.getSuccess());
        @SuppressWarnings("unchecked")
        List<com.campus.vo.ActivityPostVO> posts = (List<com.campus.vo.ActivityPostVO>) result.getData();
        assertEquals(1, posts.size());
        assertTrue(Boolean.TRUE.equals(posts.get(0).getLiked()));
    }

    @Test
    void shouldRejectCommentWhenPostDeleted() {
        doReturn(null).when(activityPostMapper).selectOne(any(), eq(true));
        ActivityPostCommentCreateDTO dto = new ActivityPostCommentCreateDTO();
        dto.setContent("评论");

        Result result = service.createComment(11L, dto);

        assertFalse(result.getSuccess());
        assertEquals("动态不存在或不可评论", result.getErrorMsg());
    }

    @Test
    void shouldQueryPostsWithTotal() {
        ActivityPost record = new ActivityPost()
                .setId(66L)
                .setActivityId(1L)
                .setUserId(100L)
                .setContent("测试动态")
                .setStatus(1)
                .setLikeCount(3)
                .setCommentCount(2)
                .setCreateTime(LocalDateTime.now());
        Page<ActivityPost> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(record));
        page.setTotal(1);
        when(activityPostMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(activityPostImageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(new User().setId(100L).setNickName("测试同学")));
        when(activityMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(publishedActivity()));
        Result result = service.queryPosts(1, 10, null, 100L);

        assertTrue(result.getSuccess());
        assertEquals(1L, result.getTotal());
    }

    private Activity publishedActivity() {
        return new Activity()
                .setId(1L)
                .setTitle("校园活动")
                .setCoverImage("cover")
                .setCategory("公益活动")
                .setStatus(2)
                .setEventStartTime(LocalDateTime.now().plusDays(1));
    }
}
