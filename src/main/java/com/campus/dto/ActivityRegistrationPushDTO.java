package com.campus.dto;

import lombok.Data;

@Data
public class ActivityRegistrationPushDTO {
    private String event;
    private ActivityRegistrationStatusDTO payload;
}
