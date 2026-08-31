package com.campus.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NotificationEventDTO {
    private List<Long> receiverUserIds;
    private String receiverRoleCode;
    private String title;
    private String content;
    private String type;
    private String bizType;
    private Long bizId;
    private LocalDateTime eventTime;
}
