package com.campus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCheckInStatsDTO {
    private Long registeredCount;
    private Long checkedInCount;
    private Long uncheckedCount;
    private Double checkInRate;
}
