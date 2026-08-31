package com.campus.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActivityCategoryTreeDTO {
    private Long id;
    private String name;
    private Integer sortNo;
    private List<ActivityTagOptionDTO> tags;
}
