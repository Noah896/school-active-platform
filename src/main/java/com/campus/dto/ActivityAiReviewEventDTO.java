package com.campus.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityAiReviewEventDTO {
    private Long activityId;
    private String promptVersion;
    private String trigger;
    private LocalDateTime eventTime;
}
