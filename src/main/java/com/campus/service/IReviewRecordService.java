package com.campus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.dto.Result;
import com.campus.entity.SysReviewRecord;

public interface IReviewRecordService extends IService<SysReviewRecord> {

    Result queryMine(String reviewType, Integer current, Integer pageSize);

    Result deleteMine(Long id);

    Result clearMine(String reviewType);

    void record(String reviewerRoleCode,
                String reviewType,
                String bizType,
                Long bizId,
                String bizTitle,
                Long targetUserId,
                String targetName,
                String action,
                String remark);
}
