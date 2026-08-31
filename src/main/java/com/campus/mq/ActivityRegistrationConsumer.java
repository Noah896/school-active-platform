package com.campus.mq;

import com.campus.dto.ActivityRegistrationEventDTO;
import com.campus.service.impl.ActivityServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${campus.activity.register.topic}",
        consumerGroup = "${campus.activity.register.consumer-group}"
)
public class ActivityRegistrationConsumer implements RocketMQListener<ActivityRegistrationEventDTO> {

    @Resource
    private ActivityServiceImpl activityService;

    @Override
    public void onMessage(ActivityRegistrationEventDTO message) {
        log.info("收到活动报名确认消息 activityId={}, userId={}, requestId={}",
                message.getActivityId(), message.getUserId(), message.getRequestId());
        activityService.confirmRegistration(message);
    }
}
