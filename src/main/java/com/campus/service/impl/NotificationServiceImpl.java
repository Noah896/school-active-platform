package com.campus.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.config.NotificationProperties;
import com.campus.dto.NotificationEventDTO;
import com.campus.dto.NotificationPushDTO;
import com.campus.dto.Result;
import com.campus.dto.UserDTO;
import com.campus.entity.SysNotification;
import com.campus.entity.SysRole;
import com.campus.entity.SysUserRole;
import com.campus.mapper.SysNotificationMapper;
import com.campus.mapper.SysRoleMapper;
import com.campus.mapper.SysUserRoleMapper;
import com.campus.service.INotificationService;
import com.campus.utils.RbacConstants;
import com.campus.utils.SystemConstants;
import com.campus.utils.UserHolder;
import com.campus.websocket.NotificationSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<SysNotificationMapper, SysNotification>
        implements INotificationService {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private NotificationProperties notificationProperties;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private NotificationSessionRegistry notificationSessionRegistry;

    @Override
    public Result queryUnreadCount() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        Long count = query().eq("receiver_user_id", user.getId()).eq("is_read", false).count();
        return Result.ok(count == null ? 0L : count);
    }

    @Override
    public Result queryNotifications(String type, Boolean isRead, Integer current, Integer pageSize) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        int currentPage = current == null || current < 1 ? 1 : current;
        int normalizedPageSize = normalizePageSize(pageSize);
        Page<SysNotification> page = new Page<>(currentPage, normalizedPageSize);
        QueryWrapper<SysNotification> wrapper = new QueryWrapper<SysNotification>()
                .eq("receiver_user_id", user.getId())
                .eq(StrUtil.isNotBlank(type), "type", type)
                .eq(isRead != null, "is_read", isRead)
                .orderByDesc("created_at")
                .orderByDesc("id");
        page(page, wrapper);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result queryDetail(Long id) {
        SysNotification notification = findOwnedNotification(id);
        if (notification == null) {
            return Result.fail("通知不存在");
        }
        return Result.ok(notification);
    }

    @Override
    public Result markRead(Long id) {
        SysNotification notification = findOwnedNotification(id);
        if (notification == null) {
            return Result.fail("通知不存在");
        }
        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return Result.ok();
        }
        SysNotification update = new SysNotification()
                .setId(notification.getId())
                .setIsRead(true)
                .setReadTime(LocalDateTime.now());
        updateById(update);
        return Result.ok();
    }

    @Override
    public Result markAllRead() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        UpdateWrapper<SysNotification> wrapper = new UpdateWrapper<>();
        wrapper.set("is_read", true)
                .set("read_time", LocalDateTime.now())
                .eq("receiver_user_id", user.getId())
                .eq("is_read", false);
        update(wrapper);
        return Result.ok();
    }

    @Override
    public Result clearMailbox() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        remove(new QueryWrapper<SysNotification>().eq("receiver_user_id", user.getId()));
        return Result.ok();
    }

    @Override
    public void notifyUsers(List<Long> receiverUserIds, String title, String content, String type, String bizType, Long bizId) {
        NotificationEventDTO event = new NotificationEventDTO();
        event.setReceiverUserIds(receiverUserIds);
        event.setTitle(title);
        event.setContent(content);
        event.setType(type);
        event.setBizType(bizType);
        event.setBizId(bizId);
        event.setEventTime(LocalDateTime.now());
        dispatch(event);
    }

    @Override
    public void notifyRole(String receiverRoleCode, String title, String content, String type, String bizType, Long bizId) {
        NotificationEventDTO event = new NotificationEventDTO();
        event.setReceiverRoleCode(receiverRoleCode);
        event.setTitle(title);
        event.setContent(content);
        event.setType(type);
        event.setBizType(bizType);
        event.setBizId(bizId);
        event.setEventTime(LocalDateTime.now());
        dispatch(event);
    }

    @Override
    public void dispatch(NotificationEventDTO event) {
        if (event == null || StrUtil.isBlank(notificationProperties.getTopic())) {
            return;
        }
        Runnable action = () -> sendNotificationEvent(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    @Override
    public void consume(NotificationEventDTO event) {
        if (event == null || StrUtil.isBlank(event.getTitle()) || StrUtil.isBlank(event.getType())) {
            return;
        }
        List<Long> receiverIds = resolveReceiverIds(event);
        if (receiverIds.isEmpty()) {
            return;
        }
        for (Long receiverId : receiverIds) {
            SysNotification notification = new SysNotification()
                    .setReceiverUserId(receiverId)
                    .setReceiverRoleCode(event.getReceiverRoleCode())
                    .setTitle(event.getTitle())
                    .setContent(StrUtil.blankToDefault(event.getContent(), ""))
                    .setType(event.getType())
                    .setBizType(event.getBizType())
                    .setBizId(event.getBizId())
                    .setIsRead(false)
                    .setCreatedAt(event.getEventTime() == null ? LocalDateTime.now() : event.getEventTime());
            save(notification);
            pushNotification(receiverId, notification);
        }
    }

    private void sendNotificationEvent(NotificationEventDTO event) {
        try {
            rocketMQTemplate.convertAndSend(notificationProperties.getTopic(), event);
        } catch (Exception e) {
            log.warn("发送通知消息失败，改走本地补偿 type={}, bizType={}, bizId={}",
                    event.getType(), event.getBizType(), event.getBizId(), e);
            consume(event);
        }
    }

    private List<Long> resolveReceiverIds(NotificationEventDTO event) {
        Set<Long> receiverIds = new LinkedHashSet<>();
        if (CollUtil.isNotEmpty(event.getReceiverUserIds())) {
            event.getReceiverUserIds().stream().filter(Objects::nonNull).forEach(receiverIds::add);
        }
        if (StrUtil.isNotBlank(event.getReceiverRoleCode())) {
            receiverIds.addAll(queryUserIdsByRole(event.getReceiverRoleCode()));
        }
        return new ArrayList<>(receiverIds);
    }

    private List<Long> queryUserIdsByRole(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new QueryWrapper<SysRole>()
                .eq("role_code", roleCode)
                .eq("status", 1)
                .last("limit 1"));
        if (role == null) {
            return Collections.emptyList();
        }
        List<SysUserRole> relations = sysUserRoleMapper.selectList(new QueryWrapper<SysUserRole>()
                .eq("role_id", role.getId()));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream()
                .map(SysUserRole::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private void pushNotification(Long receiverId, SysNotification notification) {
        NotificationPushDTO push = new NotificationPushDTO();
        push.setEvent("notification_created");
        push.setPayload(notification);
        Long unreadCount = query().eq("receiver_user_id", receiverId).eq("is_read", false).count();
        push.setUnreadCount(unreadCount == null ? 0L : unreadCount);
        notificationSessionRegistry.push(receiverId, push);
    }

    private SysNotification findOwnedNotification(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null || id == null) {
            return null;
        }
        return query().eq("id", id).eq("receiver_user_id", user.getId()).one();
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return SystemConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, 50);
    }
}
