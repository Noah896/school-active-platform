package com.campus.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActivityPostCreateDTO {
    private Long activityId;
    private String content;
    private List<String> imageUrls;
}
