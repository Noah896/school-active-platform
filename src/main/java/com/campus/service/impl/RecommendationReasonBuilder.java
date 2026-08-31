package com.campus.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RecommendationReasonBuilder {

    public String build(RecommendationServiceImpl.RecommendationCandidate candidate) {
        if (candidate == null) {
            return "为你推荐";
        }
        if (candidate.getMatchedTagNames() != null && !candidate.getMatchedTagNames().isEmpty()) {
            List<String> tags = candidate.getMatchedTagNames().stream()
                    .filter(StrUtil::isNotBlank)
                    .limit(2)
                    .collect(Collectors.toList());
            if (!tags.isEmpty()) {
                return "根据你对「" + String.join(" / ", tags) + "」的喜好推荐";
            }
        }
        if (Boolean.TRUE.equals(candidate.getMatchedPrimaryCategory()) && StrUtil.isNotBlank(candidate.getActivity().getCategory())) {
            return "根据你对「" + candidate.getActivity().getCategory() + "」的喜好推荐";
        }
        if (Boolean.TRUE.equals(candidate.getHot())) {
            return "近期报名热度较高";
        }
        if (Boolean.TRUE.equals(candidate.getVectorMatched())) {
            return Boolean.TRUE.equals(candidate.getRuleMatched())
                    ? "结合你的偏好标签和兴趣画像推荐"
                    : "根据你的近期兴趣画像语义推荐";
        }
        if (Boolean.TRUE.equals(candidate.getUpcomingSoon())) {
            return "活动即将开始，适合近期参与";
        }
        return "为你推荐";
    }
}
