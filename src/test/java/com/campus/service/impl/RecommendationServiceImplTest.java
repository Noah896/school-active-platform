package com.campus.service.impl;

import com.campus.dto.RecommendationActivityDTO;
import com.campus.dto.RecommendationPageDTO;
import com.campus.dto.Result;
import com.campus.dto.UserDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityCategory;
import com.campus.entity.ActivityRegistration;
import com.campus.entity.ActivityTag;
import com.campus.entity.ActivityTagRelation;
import com.campus.entity.UserPreferenceTag;
import com.campus.mapper.ActivityCategoryMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityPostMapper;
import com.campus.mapper.ActivityRegistrationMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.mapper.UserPreferenceTagMapper;
import com.campus.mapper.UserProfileEmbeddingMapper;
import com.campus.service.EmbeddingTaskService;
import com.campus.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityTagRelationMapper activityTagRelationMapper;
    @Mock
    private ActivityTagMapper activityTagMapper;
    @Mock
    private ActivityCategoryMapper activityCategoryMapper;
    @Mock
    private UserPreferenceTagMapper userPreferenceTagMapper;
    @Mock
    private UserProfileEmbeddingMapper userProfileEmbeddingMapper;
    @Mock
    private ActivityRegistrationMapper activityRegistrationMapper;
    @Mock
    private ActivityPostMapper activityPostMapper;
    @Mock
    private EmbeddingTaskService embeddingTaskService;
    @Mock
    private EmbeddingSearchService embeddingSearchService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecommendationServiceImpl();
        ReflectionTestUtils.setField(service, "activityMapper", activityMapper);
        ReflectionTestUtils.setField(service, "activityTagRelationMapper", activityTagRelationMapper);
        ReflectionTestUtils.setField(service, "activityTagMapper", activityTagMapper);
        ReflectionTestUtils.setField(service, "activityCategoryMapper", activityCategoryMapper);
        ReflectionTestUtils.setField(service, "userPreferenceTagMapper", userPreferenceTagMapper);
        ReflectionTestUtils.setField(service, "userProfileEmbeddingMapper", userProfileEmbeddingMapper);
        ReflectionTestUtils.setField(service, "activityRegistrationMapper", activityRegistrationMapper);
        ReflectionTestUtils.setField(service, "activityPostMapper", activityPostMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "scoreCalculator", new RecommendationScoreCalculator());
        ReflectionTestUtils.setField(service, "reasonBuilder", new RecommendationReasonBuilder());
        ReflectionTestUtils.setField(service, "embeddingTaskService", embeddingTaskService);
        ReflectionTestUtils.setField(service, "embeddingSearchService", embeddingSearchService);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        lenient().doNothing().when(embeddingTaskService).ensureUserProfileEmbedding(anyLong());
        lenient().when(embeddingSearchService.searchSimilarActivities(any(), anyInt(), anyDouble()))
                .thenReturn(Collections.emptyList());
        lenient().when(userPreferenceTagMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(userProfileEmbeddingMapper.selectOne(any())).thenReturn(null);
        lenient().when(activityRegistrationMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(activityMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(activityMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        lenient().when(activityTagRelationMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(activityTagMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(activityCategoryMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        lenient().when(activityPostMapper.selectMaps(any())).thenReturn(Collections.emptyList());

        UserDTO user = new UserDTO();
        user.setId(100L);
        user.setNickName("推荐测试用户");
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldPrioritizeTagMatchedActivityWithTagReason() {
        Activity activity1 = publishedActivity(1L, "AI 创新工坊", "创新实践", 20, 100,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(1));
        Activity activity2 = publishedActivity(2L, "摄影采风", "文艺活动", 60, 100,
                LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(4).plusHours(3), LocalDateTime.now().minusHours(2));
        when(userPreferenceTagMapper.selectList(any())).thenReturn(Collections.singletonList(
                new UserPreferenceTag().setUserId(100L).setTagId(11L).setSource("MANUAL")
        ));
        when(activityTagRelationMapper.selectList(any())).thenReturn(
                Collections.singletonList(new ActivityTagRelation().setActivityId(1L).setTagId(11L)),
                Arrays.asList(
                        new ActivityTagRelation().setActivityId(1L).setTagId(11L),
                        new ActivityTagRelation().setActivityId(2L).setTagId(12L)
                )
        );
        when(activityMapper.selectList(any())).thenReturn(
                Collections.singletonList(new Activity().setId(1L)),
                Arrays.asList(new Activity().setId(1L), new Activity().setId(2L)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        when(activityMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(activity1, activity2));
        when(activityTagMapper.selectList(any())).thenReturn(Arrays.asList(
                new ActivityTag().setId(11L).setCategoryId(101L).setName("创客活动").setStatus(1),
                new ActivityTag().setId(12L).setCategoryId(102L).setName("摄影展").setStatus(1)
        ));
        when(activityCategoryMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(
                new ActivityCategory().setId(101L).setName("创新实践"),
                new ActivityCategory().setId(102L).setName("文艺活动")
        ));

        Result result = service.queryRecommendations(1, 10);

        assertTrue(result.getSuccess());
        RecommendationPageDTO page = (RecommendationPageDTO) result.getData();
        assertFalse(Boolean.TRUE.equals(page.getFallback()));
        assertEquals(2L, page.getTotal());
        RecommendationActivityDTO matched = page.getRecords().stream()
                .filter(item -> item.getActivityId().equals(1L))
                .findFirst()
                .orElseThrow();
        assertTrue(matched.getReason().contains("创客活动"));
    }

    @Test
    void shouldFallbackWhenUserHasNoPreferences() {
        Activity hot = publishedActivity(2L, "志愿者招募", "志愿公益", 88, 120,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), LocalDateTime.now().minusDays(1));
        Activity upcoming = publishedActivity(3L, "春季音乐会", "文艺活动", 35, 120,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(2), LocalDateTime.now().minusHours(3));
        when(activityMapper.selectList(any())).thenReturn(
                Collections.singletonList(new Activity().setId(2L)),
                Collections.singletonList(new Activity().setId(3L)),
                Collections.emptyList()
        );
        when(activityMapper.selectBatchIds(anyCollection())).thenReturn(
                Arrays.asList(hot, upcoming),
                Arrays.asList(hot, upcoming)
        );
        when(activityTagRelationMapper.selectList(any())).thenReturn(Collections.emptyList());

        Result result = service.queryRecommendations(1, 10);

        assertTrue(result.getSuccess());
        RecommendationPageDTO page = (RecommendationPageDTO) result.getData();
        assertTrue(Boolean.TRUE.equals(page.getFallback()));
        assertTrue(page.getMessage().contains("热门"));
        assertNotNull(page.getRecords());
    }

    @Test
    void shouldExcludeRegisteredActivities() {
        Activity registered = publishedActivity(1L, "已报名活动", "创新实践", 40, 100,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(3), LocalDateTime.now().minusHours(2));
        Activity available = publishedActivity(2L, "可推荐活动", "创新实践", 18, 100,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(2), LocalDateTime.now().minusHours(1));
        when(userPreferenceTagMapper.selectList(any())).thenReturn(Collections.singletonList(
                new UserPreferenceTag().setUserId(100L).setTagId(11L).setSource("MANUAL")
        ));
        when(activityRegistrationMapper.selectList(any())).thenReturn(Collections.singletonList(
                new ActivityRegistration().setActivityId(1L).setUserId(100L).setStatus(1)
        ));
        when(activityTagRelationMapper.selectList(any())).thenReturn(
                Arrays.asList(
                        new ActivityTagRelation().setActivityId(1L).setTagId(11L),
                        new ActivityTagRelation().setActivityId(2L).setTagId(11L)
                ),
                Arrays.asList(
                        new ActivityTagRelation().setActivityId(1L).setTagId(11L),
                        new ActivityTagRelation().setActivityId(2L).setTagId(11L)
                )
        );
        when(activityMapper.selectList(any())).thenReturn(
                Arrays.asList(new Activity().setId(1L), new Activity().setId(2L)),
                Arrays.asList(new Activity().setId(1L), new Activity().setId(2L)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        when(activityMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(registered, available));
        when(activityTagMapper.selectList(any())).thenReturn(Collections.singletonList(
                new ActivityTag().setId(11L).setCategoryId(101L).setName("创客活动").setStatus(1)
        ));
        when(activityCategoryMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(
                new ActivityCategory().setId(101L).setName("创新实践")
        ));

        Result result = service.queryRecommendations(1, 10);

        RecommendationPageDTO page = (RecommendationPageDTO) result.getData();
        assertEquals(1L, page.getTotal());
        assertEquals(2L, page.getRecords().get(0).getActivityId());
    }

    @Test
    void shouldFilterInvalidActivitiesAndDeduplicateCandidates() {
        Activity valid = publishedActivity(1L, "有效活动", "创新实践", 12, 100,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2), LocalDateTime.now().minusDays(1));
        Activity ended = publishedActivity(2L, "已结束活动", "创新实践", 10, 100,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(3));
        Activity expired = publishedActivity(3L, "报名截止活动", "创新实践", 10, 100,
                LocalDateTime.now().plusDays(6), LocalDateTime.now().plusDays(7), LocalDateTime.now().minusDays(3))
                .setRegistrationEndTime(LocalDateTime.now().minusMinutes(1));
        Activity full = publishedActivity(4L, "已满员活动", "创新实践", 100, 100,
                LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(5), LocalDateTime.now().minusDays(2));
        Activity offline = publishedActivity(5L, "未发布活动", "创新实践", 10, 100,
                LocalDateTime.now().plusDays(4), LocalDateTime.now().plusDays(5), LocalDateTime.now().minusDays(2))
                .setStatus(1);

        when(userPreferenceTagMapper.selectList(any())).thenReturn(Collections.singletonList(
                new UserPreferenceTag().setUserId(100L).setTagId(11L).setSource("MANUAL")
        ));
        when(activityTagRelationMapper.selectList(any())).thenReturn(
                Collections.singletonList(new ActivityTagRelation().setActivityId(1L).setTagId(11L)),
                Arrays.asList(
                        new ActivityTagRelation().setActivityId(1L).setTagId(11L),
                        new ActivityTagRelation().setActivityId(2L).setTagId(11L),
                        new ActivityTagRelation().setActivityId(3L).setTagId(11L),
                        new ActivityTagRelation().setActivityId(4L).setTagId(11L),
                        new ActivityTagRelation().setActivityId(5L).setTagId(11L)
                )
        );
        when(activityMapper.selectList(any())).thenReturn(
                Arrays.asList(new Activity().setId(1L), new Activity().setId(2L), new Activity().setId(3L), new Activity().setId(4L), new Activity().setId(5L)),
                Arrays.asList(new Activity().setId(1L), new Activity().setId(2L), new Activity().setId(3L), new Activity().setId(4L), new Activity().setId(5L)),
                Arrays.asList(new Activity().setId(1L), new Activity().setId(1L)),
                Arrays.asList(new Activity().setId(1L), new Activity().setId(3L)),
                Arrays.asList(new Activity().setId(5L), new Activity().setId(1L))
        );
        when(activityMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(valid, ended, expired, full, offline));
        when(activityTagMapper.selectList(any())).thenReturn(Collections.singletonList(
                new ActivityTag().setId(11L).setCategoryId(101L).setName("创客活动").setStatus(1)
        ));
        when(activityCategoryMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(
                new ActivityCategory().setId(101L).setName("创新实践")
        ));

        Result result = service.queryRecommendations(1, 10);

        RecommendationPageDTO page = (RecommendationPageDTO) result.getData();
        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getRecords().get(0).getActivityId());
    }

    @Test
    void shouldReturnRecommendationsWhenRedisUnavailable() {
        Activity hot = publishedActivity(8L, "Redis 降级活动", "志愿公益", 30, 100,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), LocalDateTime.now().minusHours(2));
        when(valueOperations.get(anyString())).thenThrow(new DataAccessResourceFailureException("redis down"));
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        when(activityMapper.selectList(any())).thenReturn(
                Collections.singletonList(new Activity().setId(8L)),
                Collections.emptyList(),
                Collections.emptyList()
        );
        when(activityMapper.selectBatchIds(anyCollection())).thenReturn(
                Collections.singletonList(hot),
                Collections.singletonList(hot)
        );

        Result result = service.queryRecommendations(1, 10);

        assertTrue(result.getSuccess());
        RecommendationPageDTO page = (RecommendationPageDTO) result.getData();
        assertNotNull(page);
        assertTrue(Boolean.TRUE.equals(page.getFallback()));
    }

    private Activity publishedActivity(Long id,
                                       String title,
                                       String category,
                                       int registeredCount,
                                       int maxParticipants,
                                       LocalDateTime startTime,
                                       LocalDateTime endTime,
                                       LocalDateTime createTime) {
        return new Activity()
                .setId(id)
                .setTitle(title)
                .setCategory(category)
                .setCoverImage("https://img/" + id + ".png")
                .setLocation("大学生活动中心")
                .setRegisteredCount(registeredCount)
                .setMaxParticipants(maxParticipants)
                .setStatus(2)
                .setCreateTime(createTime)
                .setRegistrationEndTime(startTime.minusHours(1))
                .setEventStartTime(startTime)
                .setEventEndTime(endTime);
    }
}
