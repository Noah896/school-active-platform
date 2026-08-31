package com.campus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.dto.NotificationEventDTO;
import com.campus.dto.Result;
import com.campus.entity.SysNotification;

import java.util.List;

public interface INotificationService extends IService<SysNotification> {

    Result queryUnreadCount();

    Result queryNotifications(String type, Boolean isRead, Integer current, Integer pageSize);

    Result queryDetail(Long id);

    Result markRead(Long id);

    Result markAllRead();

    Result clearMailbox();

    void dispatch(NotificationEventDTO event);

    void consume(NotificationEventDTO event);

    void notifyUsers(List<Long> receiverUserIds, String title, String content, String type, String bizType, Long bizId);

    void notifyRole(String receiverRoleCode, String title, String content, String type, String bizType, Long bizId);
}
