package com.campus.service.impl;

import com.campus.dto.Result;
import com.campus.dto.UserDTO;
import com.campus.dto.UserPreferenceTagUpdateDTO;
import com.campus.entity.ActivityTag;
import com.campus.mapper.ActivityCategoryMapper;
import com.campus.mapper.ActivityCheckInRecordMapper;
import com.campus.mapper.ActivityFavoriteMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityRegistrationMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.mapper.ActivityVoucherMapper;
import com.campus.mapper.UserMapper;
import com.campus.mapper.UserPreferenceTagMapper;
import com.campus.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceImplRecommendationVersionTest {

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityRegistrationMapper activityRegistrationMapper;
    @Mock
    private ActivityVoucherMapper activityVoucherMapper;
    @Mock
    private ActivityCheckInRecordMapper activityCheckInRecordMapper;
    @Mock
    private ActivityFavoriteMapper activityFavoriteMapper;
    @Mock
    private ActivityCategoryMapper activityCategoryMapper;
    @Mock
    private ActivityTagMapper activityTagMapper;
    @Mock
    private ActivityTagRelationMapper activityTagRelationMapper;
    @Mock
    private UserPreferenceTagMapper userPreferenceTagMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ActivityServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", activityMapper);
        ReflectionTestUtils.setField(service, "activityRegistrationMapper", activityRegistrationMapper);
        ReflectionTestUtils.setField(service, "activityVoucherMapper", activityVoucherMapper);
        ReflectionTestUtils.setField(service, "activityCheckInRecordMapper", activityCheckInRecordMapper);
        ReflectionTestUtils.setField(service, "activityFavoriteMapper", activityFavoriteMapper);
        ReflectionTestUtils.setField(service, "activityCategoryMapper", activityCategoryMapper);
        ReflectionTestUtils.setField(service, "activityTagMapper", activityTagMapper);
        ReflectionTestUtils.setField(service, "activityTagRelationMapper", activityTagRelationMapper);
        ReflectionTestUtils.setField(service, "userPreferenceTagMapper", userPreferenceTagMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(activityTagMapper.selectList(any())).thenReturn(Collections.singletonList(
                new ActivityTag().setId(11L).setCategoryId(101L).setName("创客活动").setStatus(1)
        ));
        lenient().when(activityCategoryMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        UserDTO user = new UserDTO();
        user.setId(100L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldIncrementRecommendationVersionAfterUpdatingPreferenceTags() {
        UserPreferenceTagUpdateDTO dto = new UserPreferenceTagUpdateDTO();
        dto.setTagIds(Collections.singletonList(11L));

        Result result = service.updateMyPreferenceTags(dto);

        assertTrue(result.getSuccess());
        verify(valueOperations).increment("recommend:user:100:version");
    }
}
