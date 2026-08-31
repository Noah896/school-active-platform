package com.campus.controller;

import com.campus.dto.ActivityCheckInVerifyDTO;
import com.campus.dto.ReviewActionDTO;
import com.campus.dto.Result;
import com.campus.entity.Activity;
import com.campus.ratelimit.RateLimit;
import com.campus.service.IActivityService;
import com.campus.service.OssService;
import com.campus.utils.AuthorizationUtils;
import com.campus.utils.RbacConstants;
import com.campus.utils.SystemConstants;
import com.campus.utils.UserHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Resource
    private IActivityService activityService;

    @Resource
    private OssService ossService;

    @GetMapping("/public/list")
    @RateLimit(scene = "search")
    public Result queryPublicActivities(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "organizerName", required = false) String organizerName,
            @RequestParam(value = "stageFilter", required = false) String stageFilter,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "startTimeFrom", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTimeFrom,
            @RequestParam(value = "startTimeTo", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTimeTo,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return activityService.queryPublicActivities(keyword, category, status, location, organizerName,
                stageFilter, sortBy, startTimeFrom, startTimeTo, current, pageSize);
    }

    @GetMapping("/public/categories")
    public Result queryPublicCategories() {
        return activityService.queryPublicCategories();
    }

    @GetMapping("/public/{id}")
    @RateLimit(scene = "activity-detail-hot")
    public Result queryActivityDetail(@PathVariable("id") Long id) {
        return activityService.queryActivityDetail(id);
    }

    @PostMapping
    public Result createActivity(@RequestBody Activity activity) {
        return activityService.createActivity(activity);
    }

    @PutMapping
    public Result updateActivity(@RequestBody Activity activity) {
        return activityService.updateActivity(activity);
    }

    @GetMapping("/manage/mine")
    public Result queryMyCreatedActivities(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return activityService.queryMyCreatedActivities(keyword, current, pageSize);
    }

    @PostMapping("/manage/{id}/offline-apply")
    public Result requestOfflineActivity(@PathVariable("id") Long activityId, @RequestBody(required = false) ReviewActionDTO dto) {
        return activityService.requestOfflineActivity(activityId, dto);
    }

    @PostMapping("/{id}/register")
    @RateLimit(scene = "register")
    public Result register(@PathVariable("id") Long activityId) {
        return activityService.register(activityId);
    }

    @GetMapping("/{id}/register/status")
    public Result queryRegistrationStatus(@PathVariable("id") Long activityId) {
        return activityService.queryRegistrationStatus(activityId);
    }

    @DeleteMapping("/{id}/register")
    public Result cancelRegistration(@PathVariable("id") Long activityId) {
        return activityService.cancelRegistration(activityId);
    }

    @PostMapping("/{id}/favorite")
    public Result favoriteActivity(@PathVariable("id") Long activityId) {
        return activityService.favoriteActivity(activityId);
    }

    @DeleteMapping("/{id}/favorite")
    public Result unfavoriteActivity(@PathVariable("id") Long activityId) {
        return activityService.unfavoriteActivity(activityId);
    }

    @GetMapping("/favorite/mine")
    public Result queryMyFavoriteActivities(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return activityService.queryMyFavoriteActivities(keyword, current, pageSize);
    }

    @GetMapping("/registration/mine")
    public Result queryMyRegistrations(
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return activityService.queryMyRegistrations(filter, keyword, current, pageSize);
    }

    @DeleteMapping("/registration/mine/{registrationId}")
    public Result deleteMyRegistration(@PathVariable("registrationId") Long registrationId) {
        return activityService.deleteMyRegistration(registrationId);
    }

    @GetMapping("/manage/{id}/registrations")
    public Result queryActivityRegistrations(
            @PathVariable("id") Long activityId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return activityService.queryActivityRegistrations(activityId, current, pageSize);
    }

    @GetMapping("/manage/registration-reviews")
    public Result queryMyPendingRegistrationReviews(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return activityService.queryMyPendingRegistrationReviews(current, pageSize);
    }

    @PostMapping("/manage/{id}/registrations/{registrationId}/review")
    public Result reviewRegistration(@PathVariable("id") Long activityId,
                                     @PathVariable("registrationId") Long registrationId,
                                     @RequestBody ReviewActionDTO dto) {
        return activityService.reviewRegistration(activityId, registrationId, dto);
    }

    @PostMapping("/manage/{id}/registrations/{registrationId}/cancel-review")
    public Result reviewCancelRegistration(@PathVariable("id") Long activityId,
                                           @PathVariable("registrationId") Long registrationId,
                                           @RequestBody ReviewActionDTO dto) {
        return activityService.reviewCancelRegistration(activityId, registrationId, dto);
    }

    @PostMapping("/manage/image")
    public Result uploadActivityImage(@RequestParam("file") MultipartFile file) {
        if (!AuthorizationUtils.hasPermission(UserHolder.getUser(), RbacConstants.PERM_ACTIVITY_CREATE)) {
            return Result.fail("无权上传活动图片");
        }
        String url = ossService.uploadActivityImage(UserHolder.getUser().getId(), file);
        return Result.ok(url);
    }

    @PostMapping("/manage/{id}/check-in/verify")
    public Result verifyCheckIn(
            @PathVariable("id") Long activityId,
            @RequestBody ActivityCheckInVerifyDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return activityService.verifyCheckIn(activityId, dto, idempotencyKey);
    }

    @GetMapping("/manage/{id}/check-in/stats")
    public Result queryCheckInStats(@PathVariable("id") Long activityId) {
        return activityService.queryCheckInStats(activityId);
    }

    @GetMapping("/manage/{id}/check-in/records")
    public Result queryCheckInRecords(
            @PathVariable("id") Long activityId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return activityService.queryCheckInRecords(activityId, current, pageSize);
    }

    @GetMapping("/manage/{id}/check-in/dashboard")
    public Result queryCheckInDashboard(@PathVariable("id") Long activityId) {
        return activityService.queryCheckInDashboard(activityId);
    }

    @GetMapping("/admin/review-list")
    @RateLimit(scene = "review-list")
    public Result queryPendingReviewActivities(@RequestParam(value = "keyword", required = false) String keyword) {
        if (!AuthorizationUtils.hasPermission(UserHolder.getUser(), RbacConstants.PERM_ACTIVITY_APPROVE)) {
            return Result.fail("无权查看待审核活动");
        }
        return activityService.queryPendingReviewActivities(keyword);
    }

    @GetMapping("/admin/{id}/ai-review")
    public Result queryActivityAiReview(@PathVariable("id") Long activityId) {
        if (!AuthorizationUtils.hasPermission(UserHolder.getUser(), RbacConstants.PERM_ACTIVITY_APPROVE)) {
            return Result.fail("无权查看 AI 审核建议");
        }
        return activityService.queryActivityAiReview(activityId);
    }

    @GetMapping("/admin/published-list")
    public Result queryPublishedActivitiesForAdmin(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        if (!AuthorizationUtils.hasPermission(UserHolder.getUser(), RbacConstants.PERM_ACTIVITY_OFFLINE)) {
            return Result.fail("无权查看已发布活动");
        }
        return activityService.queryPublishedActivitiesForAdmin(keyword, current, pageSize);
    }

    @PostMapping("/admin/{id}/review")
    public Result reviewActivity(@PathVariable("id") Long activityId, @RequestBody ReviewActionDTO dto) {
        if (!AuthorizationUtils.hasPermission(UserHolder.getUser(), RbacConstants.PERM_ACTIVITY_APPROVE)) {
            return Result.fail("无权审核活动");
        }
        return activityService.reviewActivity(activityId, dto);
    }

    @PostMapping("/admin/{id}/offline")
    public Result offlineActivity(@PathVariable("id") Long activityId, @RequestBody(required = false) ReviewActionDTO dto) {
        if (!AuthorizationUtils.hasPermission(UserHolder.getUser(), RbacConstants.PERM_ACTIVITY_OFFLINE)) {
            return Result.fail("无权下架活动");
        }
        return activityService.offlineActivity(activityId, dto);
    }
}
