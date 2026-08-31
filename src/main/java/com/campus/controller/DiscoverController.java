package com.campus.controller;

import com.campus.dto.ActivityPostCommentCreateDTO;
import com.campus.dto.ActivityPostCreateDTO;
import com.campus.dto.Result;
import com.campus.service.IActivityPostService;
import com.campus.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/discover")
public class DiscoverController {

    @Resource
    private IActivityPostService activityPostService;

    @Resource
    private RecommendationService recommendationService;

    @GetMapping("/posts")
    public Result queryPosts(@RequestParam(value = "current", defaultValue = "1") Integer current,
                             @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                             @RequestParam(value = "activityId", required = false) Long activityId,
                             @RequestParam(value = "userId", required = false) Long userId) {
        return activityPostService.queryPosts(current, pageSize, activityId, userId);
    }

    @PostMapping("/posts")
    public Result createPost(@RequestBody ActivityPostCreateDTO dto) {
        return activityPostService.createPost(dto);
    }

    @DeleteMapping("/posts/{postId}")
    public Result deletePost(@PathVariable("postId") Long postId) {
        return activityPostService.deletePost(postId);
    }

    @PostMapping("/posts/{postId}/like")
    public Result likePost(@PathVariable("postId") Long postId) {
        return activityPostService.likePost(postId);
    }

    @DeleteMapping("/posts/{postId}/like")
    public Result unlikePost(@PathVariable("postId") Long postId) {
        return activityPostService.unlikePost(postId);
    }

    @GetMapping("/posts/{postId}/comments")
    public Result queryComments(@PathVariable("postId") Long postId,
                                @RequestParam(value = "current", defaultValue = "1") Integer current,
                                @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return activityPostService.queryComments(postId, current, pageSize);
    }

    @PostMapping("/posts/{postId}/comments")
    public Result createComment(@PathVariable("postId") Long postId,
                                @RequestBody ActivityPostCommentCreateDTO dto) {
        return activityPostService.createComment(postId, dto);
    }

    @DeleteMapping("/comments/{commentId}")
    public Result deleteComment(@PathVariable("commentId") Long commentId) {
        return activityPostService.deleteComment(commentId);
    }

    @GetMapping("/recommendations")
    public Result queryRecommendations(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                       @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return recommendationService.queryRecommendations(current, pageSize);
    }

    @GetMapping("/eligible-activities")
    public Result queryEligibleActivities() {
        return activityPostService.queryEligibleActivities();
    }
}
