package com.campus.dto;

import lombok.Data;

@Data
public class ActivityTagOptionDTO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private Integer sortNo;
}
