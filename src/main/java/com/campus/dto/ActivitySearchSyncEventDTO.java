package com.campus.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivitySearchSyncEventDTO {
    private Long activityId;
    private String trigger;
    private LocalDateTime eventTime;
}
