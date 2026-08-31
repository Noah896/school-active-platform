package com.campus.controller;

import com.campus.dto.Result;
import com.campus.service.OssService;
import com.campus.utils.UserHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("upload")
public class UploadController {

    @Resource
    private OssService ossService;

    @PostMapping("activity")
    public Result uploadActivityImage(@RequestParam("file") MultipartFile image) {
        return Result.ok(ossService.uploadActivityImage(UserHolder.getUser().getId(), image));
    }

    @PostMapping("discover-image")
    public Result uploadDiscoverImage(@RequestParam("file") MultipartFile image) {
        return Result.ok(ossService.uploadDiscoverImage(UserHolder.getUser().getId(), image));
    }
}
