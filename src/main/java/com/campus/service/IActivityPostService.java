package com.campus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.dto.ActivityPostCommentCreateDTO;
import com.campus.dto.ActivityPostCreateDTO;
import com.campus.dto.Result;
import com.campus.entity.ActivityPost;

public interface IActivityPostService extends IService<ActivityPost> {

    Result queryPosts(Integer current, Integer pageSize, Long activityId, Long userId);

    Result createPost(ActivityPostCreateDTO dto);

    Result deletePost(Long postId);

    Result likePost(Long postId);

    Result unlikePost(Long postId);

    Result queryComments(Long postId, Integer current, Integer pageSize);

    Result createComment(Long postId, ActivityPostCommentCreateDTO dto);

    Result deleteComment(Long commentId);

    Result queryEligibleActivities();
}
