package com.campus.controller;

import com.campus.dto.Result;
import com.campus.service.IReviewRecordService;
import com.campus.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/review-records")
public class ReviewRecordController {

    @Resource
    private IReviewRecordService reviewRecordService;

    @GetMapping("/mine")
    public Result queryMine(@RequestParam(value = "reviewType", required = false) String reviewType,
                            @RequestParam(value = "current", defaultValue = "1") Integer current,
                            @RequestParam(value = "pageSize", defaultValue = "" + SystemConstants.MAX_PAGE_SIZE) Integer pageSize) {
        return reviewRecordService.queryMine(reviewType, current, pageSize);
    }

    @DeleteMapping("/{id}")
    public Result deleteMine(@PathVariable("id") Long id) {
        return reviewRecordService.deleteMine(id);
    }

    @DeleteMapping("/clear")
    public Result clearMine(@RequestParam(value = "reviewType", required = false) String reviewType) {
        return reviewRecordService.clearMine(reviewType);
    }
}
