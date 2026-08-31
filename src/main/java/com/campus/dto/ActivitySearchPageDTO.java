package com.campus.dto;

import com.campus.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySearchPageDTO {
    private List<Activity> records;
    private Long total;
}
