package com.campus.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserPreferenceTagUpdateDTO {
    private List<Long> tagIds;
}
