package com.campus.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActivityPostVO {
    private Long id;
    private String content;
    private List<String> imageUrls;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private Long userId;
    private String nickName;
    private String icon;

    private Long activityId;
    private String activityTitle;
    private String activityCoverImage;
    private String activityCategory;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime activityStartTime;
    private String activityStartTimeText;
    private String activityStatusText;

    private Integer likeCount;
    private Integer commentCount;
    private Boolean liked;
}
