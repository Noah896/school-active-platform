package com.campus.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class RecommendationPageDTO {

    private List<RecommendationActivityDTO> records = Collections.emptyList();
    private Long total = 0L;
    private Boolean fallback = Boolean.FALSE;
    private String message;
}
