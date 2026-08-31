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
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_activity")
public class Activity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private String organizerName;
    private String title;
    private String coverImage;
    private String images;
    private String summary;
    private String content;
    private String activityFlow;
    private String faq;
    private String category;
    private String customCategory;
    private String registrationMode;
    private String contactInfo;
    private String location;
    private Integer maxParticipants;
    private Integer registeredCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationEndTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventEndTime;

    private Boolean checkInEnabled;
    private String checkInCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkInCodeExpireTime;

    private Integer status;
    private Long reviewerId;
    private String reviewRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Boolean registered;

    @TableField(exist = false)
    private Boolean canManage;

    @TableField(exist = false)
    private Boolean checkedIn;

    @TableField(exist = false)
    private Boolean registrationOpen;

    @TableField(exist = false)
    private String registrationStatus;

    @TableField(exist = false)
    private String registrationMessage;

    @TableField(exist = false)
    private String registrationRequestId;

    @TableField(exist = false)
    private String registrationFailReason;

    @TableField(exist = false)
    private Long voucherId;

    @TableField(exist = false)
    private String voucherDisplayCode;

    @TableField(exist = false)
    private String voucherStatus;

    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime voucherIssuedTime;

    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime voucherCheckedInTime;

    @TableField(exist = false)
    private Boolean favorited;

    @TableField(exist = false)
    private List<Long> tagIds;

    @TableField(exist = false)
    private List<ActivityTag> tags;

    @TableField(exist = false)
    private String displayCategory;
}
