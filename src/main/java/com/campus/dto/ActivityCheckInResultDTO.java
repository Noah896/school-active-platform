package com.campus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityCheckInResultDTO {
    private Long activityId;
    private Long voucherId;
    private String displayCode;
    private Long userId;
    private String userNickName;
    private String voucherStatus;
    private String resultStatus;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedInTime;

    private Long checkedInBy;
}
