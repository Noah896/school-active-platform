package com.campus.dto;

import com.campus.entity.ActivityCheckInRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActivityCheckInDashboardDTO {
    private ActivityCheckInSummaryDTO activitySummary;
    private ActivityCheckInStatsDTO stats;
    private List<ActivityTrendPointDTO> statusChart = new ArrayList<>();
    private List<ActivityTrendPointDTO> registrationTrendChart = new ArrayList<>();
    private List<ActivityTrendPointDTO> checkInTrendChart = new ArrayList<>();
    private List<ActivityCheckInRecord> recentRecords = new ArrayList<>();
}
