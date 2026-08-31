package com.campus.mq;

import com.campus.dto.NotificationEventDTO;
import com.campus.service.INotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${campus.notification.topic}",
        consumerGroup = "${campus.notification.consumer-group}"
)
public class NotificationConsumer implements RocketMQListener<NotificationEventDTO> {

    @Resource
    private INotificationService notificationService;

    @Override
    public void onMessage(NotificationEventDTO message) {
        log.info("收到通知事件 type={}, bizType={}, bizId={}",
                message.getType(), message.getBizType(), message.getBizId());
        notificationService.consume(message);
    }
}
