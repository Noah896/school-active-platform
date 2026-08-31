package com.campus.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.dto.Result;
import com.campus.dto.UserDTO;
import com.campus.entity.SysReviewRecord;
import com.campus.mapper.SysReviewRecordMapper;
import com.campus.service.IReviewRecordService;
import com.campus.utils.SystemConstants;
import com.campus.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReviewRecordServiceImpl extends ServiceImpl<SysReviewRecordMapper, SysReviewRecord>
        implements IReviewRecordService {

    @Override
    public Result queryMine(String reviewType, Integer current, Integer pageSize) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        Page<SysReviewRecord> page = new Page<>(
                current == null || current < 1 ? 1 : current,
                normalizePageSize(pageSize)
        );
        QueryWrapper<SysReviewRecord> wrapper = new QueryWrapper<SysReviewRecord>()
                .eq("reviewer_user_id", user.getId())
                .eq(StrUtil.isNotBlank(reviewType), "review_type", reviewType)
                .orderByDesc("created_at")
                .orderByDesc("id");
        page(page, wrapper);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result deleteMine(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        if (id == null) {
            return Result.fail("记录ID不能为空");
        }
        remove(new QueryWrapper<SysReviewRecord>()
                .eq("id", id)
                .eq("reviewer_user_id", user.getId()));
        return Result.ok();
    }

    @Override
    public Result clearMine(String reviewType) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        remove(new QueryWrapper<SysReviewRecord>()
                .eq("reviewer_user_id", user.getId())
                .eq(StrUtil.isNotBlank(reviewType), "review_type", reviewType));
        return Result.ok();
    }

    @Override
    public void record(String reviewerRoleCode,
                       String reviewType,
                       String bizType,
                       Long bizId,
                       String bizTitle,
                       Long targetUserId,
                       String targetName,
                       String action,
                       String remark) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return;
        }
        save(new SysReviewRecord()
                .setReviewerUserId(user.getId())
                .setReviewerRoleCode(reviewerRoleCode)
                .setReviewType(reviewType)
                .setBizType(bizType)
                .setBizId(bizId)
                .setBizTitle(StrUtil.blankToDefault(bizTitle, "未命名"))
                .setTargetUserId(targetUserId)
                .setTargetName(StrUtil.blankToDefault(targetName, ""))
                .setAction(action)
                .setRemark(StrUtil.blankToDefault(remark, ""))
                .setCreatedAt(LocalDateTime.now()));
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return SystemConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, 50);
    }
}
