package com.campus.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ActivityAiReviewReportDTO {
    private Long activityId;
    private String taskStatus;
    private String suggestion;
    private String riskLevel;
    private Integer score;
    private List<String> problems;
    private List<String> missingFields;
    private List<Map<String, Object>> similarActivities;
    private String similarityAnalysis;
    private String reviewComment;
    private String modelName;
    private String promptVersion;
    private String parseStatus;
    private String errorMessage;
}
