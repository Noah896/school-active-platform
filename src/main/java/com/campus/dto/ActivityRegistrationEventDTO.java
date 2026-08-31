package com.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ActivityRegistrationEventDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long activityId;
    private Long userId;
    private String requestId;
    private LocalDateTime createTime;
}
