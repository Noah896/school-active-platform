package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("tb_ai_review_record")
public class ActivityAiReviewRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long activityId;
    private String taskStatus;
    private String suggestion;
    private String riskLevel;
    private Integer score;
    private String problemsJson;
    private String missingFieldsJson;
    private String similarActivitiesJson;
    private String similarityAnalysis;
    private String reviewComment;
    private String modelName;
    private String promptVersion;
    private String rawResponse;
    private String parseStatus;
    private String errorMessage;
    private String manualReviewResult;
    private String manualReviewRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime manualReviewedAt;

    private Boolean isAiAdopted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
