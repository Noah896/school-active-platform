package com.campus.service;

import com.campus.dto.ActivityAiReviewEventDTO;
import com.campus.dto.ActivityAiReviewReportDTO;
import com.campus.entity.Activity;

public interface IActivityAiReviewService {

    void scheduleActivityReview(Activity activity, String trigger);

    void consume(ActivityAiReviewEventDTO event);

    ActivityAiReviewReportDTO queryReport(Long activityId);

    void recordManualReview(Activity activity, Boolean approved, String remark);
}
