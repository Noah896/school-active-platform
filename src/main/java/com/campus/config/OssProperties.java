package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {
    /**
     * e.g. oss-cn-hangzhou.aliyuncs.com 或 https://oss-cn-hangzhou.aliyuncs.com
     */
    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;

    /**
     * Object key 前缀，例如 avatars/
     */
    private String avatarPrefix = "avatars/";

    /**
     * 活动图片 Object key 前缀，例如 activities/
     */
    private String activityPrefix = "activities/";

    /**
     * 校园圈图片 Object key 前缀，例如 discover/
     */
    private String discoverPrefix = "discover/";
}
