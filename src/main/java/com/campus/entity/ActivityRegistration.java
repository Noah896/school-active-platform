package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_activity_registration")
public class ActivityRegistration implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    private Integer status;
    private String requestId;
    private String failReason;
    private Integer checkInStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkInTime;

    private Long voucherId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String userNickName;

    @TableField(exist = false)
    private String userPhone;

    @TableField(exist = false)
    private String userIcon;

    @TableField(exist = false)
    private String activityTitle;

    @TableField(exist = false)
    private String activityCoverImage;

    @TableField(exist = false)
    private String coverImage;

    @TableField(exist = false)
    private String category;

    @TableField(exist = false)
    private String location;

    @TableField(exist = false)
    private String organizerName;

    @TableField(exist = false)
    private String registrationMode;

    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventStartTime;

    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventEndTime;

    @TableField(exist = false)
    private Boolean checkInEnabled;

    @TableField(exist = false)
    private String voucherDisplayCode;

    @TableField(exist = false)
    private String voucherStatus;

    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime voucherIssuedTime;

    @TableField(exist = false)
    private Long checkedInBy;

    @TableField(exist = false)
    private String checkedInByName;

    @TableField(exist = false)
    private String statusText;
}
