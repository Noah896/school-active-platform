package com.campus.dto;

import com.campus.entity.SysNotification;
import lombok.Data;

@Data
public class NotificationPushDTO {
    private String event;
    private SysNotification payload;
    private Long unreadCount;
}
