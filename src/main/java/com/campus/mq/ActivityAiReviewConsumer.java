package com.campus.mq;

import com.campus.dto.ActivityAiReviewEventDTO;
import com.campus.service.IActivityAiReviewService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${campus.activity.ai-review.topic}",
        consumerGroup = "${campus.activity.ai-review.consumer-group}"
)
public class ActivityAiReviewConsumer implements RocketMQListener<ActivityAiReviewEventDTO> {

    @Resource
    private IActivityAiReviewService activityAiReviewService;

    @Override
    public void onMessage(ActivityAiReviewEventDTO message) {
        log.info("收到活动 AI 审核任务 activityId={}, promptVersion={}, trigger={}",
                message.getActivityId(), message.getPromptVersion(), message.getTrigger());
        activityAiReviewService.consume(message);
    }
}
