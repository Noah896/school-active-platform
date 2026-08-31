package com.campus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityRegistrationStatusDTO {
    private Long activityId;
    private Long userId;
    private String requestId;
    private String status;
    private String message;
    private String failReason;
    private Long voucherId;
    private String voucherDisplayCode;
    private String voucherStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime voucherIssuedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime voucherCheckedInTime;
}
