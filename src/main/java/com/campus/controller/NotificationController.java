package com.campus.controller;

import com.campus.dto.Result;
import com.campus.ratelimit.RateLimit;
import com.campus.service.INotificationService;
import com.campus.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Resource
    private INotificationService notificationService;

    @GetMapping("/unread-count")
    @RateLimit(scene = "notification-unread")
    public Result queryUnreadCount() {
        return notificationService.queryUnreadCount();
    }

    @GetMapping("/list")
    @RateLimit(scene = "notification-list")
    public Result queryNotifications(@RequestParam(value = "type", required = false) String type,
                                     @RequestParam(value = "isRead", required = false) Boolean isRead,
                                     @RequestParam(value = "current", defaultValue = "1") Integer current,
                                     @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return notificationService.queryNotifications(type, isRead, current, pageSize);
    }

    @GetMapping("/{id}")
    public Result queryDetail(@PathVariable("id") Long id) {
        return notificationService.queryDetail(id);
    }

    @PutMapping("/{id}/read")
    public Result markRead(@PathVariable("id") Long id) {
        return notificationService.markRead(id);
    }

    @PutMapping("/read-all")
    public Result markAllRead() {
        return notificationService.markAllRead();
    }

    @DeleteMapping("/clear")
    public Result clearMailbox() {
        return notificationService.clearMailbox();
    }
}
