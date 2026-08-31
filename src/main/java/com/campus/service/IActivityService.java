package com.campus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.dto.ActivityCheckInVerifyDTO;
import com.campus.dto.ReviewActionDTO;
import com.campus.dto.Result;
import com.campus.dto.UserPreferenceTagUpdateDTO;
import com.campus.entity.Activity;

import java.time.LocalDateTime;

public interface IActivityService extends IService<Activity> {

    Result queryPublicActivities(String keyword,
                                 String category,
                                 Integer status,
                                 String location,
                                 String organizerName,
                                 String stageFilter,
                                 String sortBy,
                                 LocalDateTime startTimeFrom,
                                 LocalDateTime startTimeTo,
                                 Integer current,
                                 Integer pageSize);

    Result queryPublicCategories();

    Result queryMyPreferenceTags();

    Result updateMyPreferenceTags(UserPreferenceTagUpdateDTO dto);

    Result queryActivityDetail(Long id);

    Result rateLimitFallbackPublicActivities(String category, Integer current, Integer pageSize);

    Result rateLimitFallbackActivityDetail(Long id);

    boolean shouldApplyRegisterRateLimit(Long activityId);

    Result createActivity(Activity activity);

    Result updateActivity(Activity activity);

    Result queryMyCreatedActivities(String keyword, Integer current, Integer pageSize);

    Result requestOfflineActivity(Long activityId, ReviewActionDTO dto);

    Result register(Long activityId);

    Result queryRegistrationStatus(Long activityId);

    Result cancelRegistration(Long activityId);

    Result favoriteActivity(Long activityId);

    Result unfavoriteActivity(Long activityId);

    Result queryMyFavoriteActivities(String keyword, Integer current, Integer pageSize);

    Result queryMyRegistrations(String filter, String keyword, Integer current, Integer pageSize);

    Result deleteMyRegistration(Long registrationId);

    Result queryActivityRegistrations(Long activityId, Integer current, Integer pageSize);

    Result queryMyPendingRegistrationReviews(Integer current, Integer pageSize);

    Result reviewRegistration(Long activityId, Long registrationId, ReviewActionDTO dto);

    Result reviewCancelRegistration(Long activityId, Long registrationId, ReviewActionDTO dto);

    Result verifyCheckIn(Long activityId, ActivityCheckInVerifyDTO dto, String idempotencyKey);

    Result queryCheckInStats(Long activityId);

    Result queryCheckInRecords(Long activityId, Integer current, Integer pageSize);

    Result queryCheckInDashboard(Long activityId);

    Result queryPendingReviewActivities(String keyword);

    Result queryActivityAiReview(Long activityId);

    Result queryPublishedActivitiesForAdmin(String keyword, Integer current, Integer pageSize);

    Result reviewActivity(Long activityId, ReviewActionDTO dto);

    Result offlineActivity(Long activityId, ReviewActionDTO dto);
}
