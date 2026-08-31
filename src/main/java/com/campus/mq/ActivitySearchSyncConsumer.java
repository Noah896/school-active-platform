package com.campus.mq;

import com.campus.dto.ActivitySearchSyncEventDTO;
import com.campus.service.ActivitySearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${campus.activity.search.sync.topic}",
        consumerGroup = "${campus.activity.search.sync.consumer-group}"
)
public class ActivitySearchSyncConsumer implements RocketMQListener<ActivitySearchSyncEventDTO> {

    @Resource
    private ActivitySearchService activitySearchService;

    @Override
    public void onMessage(ActivitySearchSyncEventDTO message) {
        if (message == null || message.getActivityId() == null) {
            return;
        }
        log.info("收到活动搜索索引同步消息 activityId={}, trigger={}",
                message.getActivityId(), message.getTrigger());
        activitySearchService.syncActivity(message.getActivityId());
    }
}
