package com.campus.service;

import com.campus.dto.Result;

public interface RecommendationService {

    Result queryRecommendations(Integer current, Integer pageSize);
}
