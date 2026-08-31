package com.campus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RecommendationActivityDTO {

    private Long activityId;
    private String title;
    private String coverImage;
    private String categoryName;
    private String displayCategory;
    private List<String> tags;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    private String location;
    private Double score;
    private String reason;
}
