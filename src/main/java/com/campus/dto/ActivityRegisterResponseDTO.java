package com.campus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRegisterResponseDTO {
    private Long activityId;
    private String requestId;
    private String status;
    private String message;
}
