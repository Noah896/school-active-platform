SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `tb_activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `creator_id` bigint unsigned NOT NULL,
  `organizer_name` varchar(128) NOT NULL,
  `title` varchar(255) NOT NULL,
  `cover_image` varchar(1024) DEFAULT NULL,
  `images` varchar(4096) DEFAULT NULL,
  `summary` varchar(512) DEFAULT NULL,
  `content` text,
  `activity_flow` text COMMENT '活动流程',
  `faq` text COMMENT '常见问题',
  `category` varchar(64) NOT NULL COMMENT '一级分类名称',
  `custom_category` varchar(64) DEFAULT NULL COMMENT '历史自定义分类保留字段，新逻辑不再写入',
  `registration_mode` varchar(64) NOT NULL DEFAULT 'AUDIT_REQUIRED' COMMENT '报名模式：AUDIT_REQUIRED/FIRST_COME_FIRST_SERVED',
  `contact_info` varchar(255) DEFAULT NULL COMMENT '主办方联系方式',
  `location` varchar(255) NOT NULL,
  `max_participants` int NOT NULL,
  `registered_count` int NOT NULL DEFAULT 0,
  `registration_start_time` datetime NOT NULL,
  `registration_end_time` datetime NOT NULL,
  `event_start_time` datetime NOT NULL,
  `event_end_time` datetime NOT NULL,
  `check_in_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `check_in_code` varchar(64) DEFAULT NULL,
  `check_in_code_expire_time` datetime DEFAULT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 2 COMMENT '状态：1待审核，2已发布，3已驳回，4强制下架，5下架待审核',
  `reviewer_id` bigint unsigned DEFAULT NULL COMMENT '审核人ID',
  `review_remark` varchar(512) DEFAULT NULL COMMENT '审核备注/驳回原因',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_creator` (`creator_id`),
  KEY `idx_activity_category` (`category`),
  KEY `idx_activity_event_start` (`event_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_activity_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动一级分类表';

CREATE TABLE IF NOT EXISTS `tb_activity_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `category_id` bigint unsigned NOT NULL,
  `name` varchar(64) NOT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_tag_category_name` (`category_id`,`name`),
  KEY `idx_activity_tag_category` (`category_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动二级标签表';

CREATE TABLE IF NOT EXISTS `tb_activity_tag_relation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `activity_id` bigint unsigned NOT NULL,
  `tag_id` bigint unsigned NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_tag_relation` (`activity_id`,`tag_id`),
  KEY `idx_activity_tag_relation_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动标签关联表';

CREATE TABLE IF NOT EXISTS `tb_user_preference_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `tag_id` bigint unsigned NOT NULL,
  `source` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_preference_tag` (`user_id`,`tag_id`,`source`),
  KEY `idx_user_preference_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动偏好标签表';

CREATE TABLE IF NOT EXISTS `tb_activity_registration` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `activity_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '报名状态：0报名待审核，1报名通过，2报名驳回，3已退出，4退出待审核',
  `request_id` varchar(64) DEFAULT NULL,
  `fail_reason` varchar(255) DEFAULT NULL,
  `voucher_id` bigint unsigned DEFAULT NULL,
  `check_in_status` tinyint(1) NOT NULL DEFAULT 0,
  `check_in_time` datetime DEFAULT NULL,
  `confirm_time` datetime DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`,`user_id`),
  KEY `idx_registration_user` (`user_id`),
  KEY `idx_registration_voucher` (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_activity_voucher` (
  `id` bigint unsigned NOT NULL,
  `display_code` varchar(32) NOT NULL,
  `activity_id` bigint unsigned NOT NULL,
  `registration_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'UNUSED',
  `issued_time` datetime NOT NULL,
  `checked_in_time` datetime DEFAULT NULL,
  `checked_in_by` bigint unsigned DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voucher_display_code` (`display_code`),
  UNIQUE KEY `uk_voucher_registration` (`registration_id`),
  KEY `idx_voucher_activity_user` (`activity_id`,`user_id`),
  KEY `idx_voucher_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_activity_check_in_record` (
  `id` bigint unsigned NOT NULL,
  `activity_id` bigint unsigned NOT NULL,
  `voucher_id` bigint unsigned DEFAULT NULL,
  `user_id` bigint unsigned DEFAULT NULL,
  `operator_id` bigint unsigned NOT NULL,
  `result_status` varchar(64) NOT NULL,
  `request_key` varchar(128) DEFAULT NULL,
  `request_fingerprint` varchar(128) DEFAULT NULL,
  `response_body` varchar(2048) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_check_in_record_activity` (`activity_id`),
  KEY `idx_check_in_record_voucher` (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_activity_favorite` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `activity_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_favorite_user_activity` (`user_id`,`activity_id`),
  KEY `idx_activity_favorite_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动收藏表';

CREATE TABLE IF NOT EXISTS `tb_activity_post` (
  `id` bigint unsigned NOT NULL,
  `activity_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `content` varchar(1000) NOT NULL,
  `visibility` tinyint(1) NOT NULL DEFAULT 1 COMMENT '可见性：1公开',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1正常 2已删除 3已隐藏',
  `like_count` int NOT NULL DEFAULT 0,
  `comment_count` int NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_post_activity` (`activity_id`,`status`,`create_time`),
  KEY `idx_activity_post_user` (`user_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态表';

CREATE TABLE IF NOT EXISTS `tb_activity_post_image` (
  `id` bigint unsigned NOT NULL,
  `post_id` bigint unsigned NOT NULL,
  `image_url` varchar(1024) NOT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_post_image_post` (`post_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态图片表';

CREATE TABLE IF NOT EXISTS `tb_activity_post_like` (
  `id` bigint unsigned NOT NULL,
  `post_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_post_like_post_user` (`post_id`,`user_id`),
  KEY `idx_activity_post_like_user` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态点赞表';

CREATE TABLE IF NOT EXISTS `tb_activity_post_comment` (
  `id` bigint unsigned NOT NULL,
  `post_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `content` varchar(200) NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1正常 2已删除 3已隐藏',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_post_comment_post` (`post_id`,`status`,`create_time`),
  KEY `idx_activity_post_comment_user` (`user_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态评论表';

CREATE TABLE IF NOT EXISTS `tb_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `phone` varchar(11) NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  `password` varchar(128) DEFAULT '',
  `nick_name` varchar(32) DEFAULT '',
  `icon` varchar(255) DEFAULT '',
  `role_type` tinyint(1) NOT NULL DEFAULT 2,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniqe_key_phone` (`phone`),
  UNIQUE KEY `uk_tb_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_user_info` (
  `user_id` bigint unsigned NOT NULL,
  `city` varchar(64) DEFAULT '',
  `college` varchar(64) DEFAULT '',
  `grade` varchar(32) DEFAULT '',
  `mentor` varchar(64) DEFAULT '',
  `introduce` varchar(128) DEFAULT NULL,
  `gender` tinyint unsigned DEFAULT 0,
  `birthday` date DEFAULT NULL,
  `credits` int unsigned DEFAULT 0,
  `level` tinyint unsigned DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `organizer_apply` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `apply_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `org_name` varchar(128) NOT NULL,
  `reason` varchar(512) DEFAULT NULL,
  `reviewer_id` bigint unsigned DEFAULT NULL,
  `review_remark` varchar(255) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_organizer_apply_user` (`user_id`),
  KEY `idx_organizer_apply_status` (`apply_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `role_code` varchar(64) NOT NULL,
  `role_name` varchar(128) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `permission_code` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_type` varchar(32) NOT NULL DEFAULT 'API',
  `path` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `role_id` bigint unsigned NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`,`role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `role_id` bigint unsigned NOT NULL,
  `permission_id` bigint unsigned NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`,`permission_id`),
  KEY `idx_sys_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_notification` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `receiver_user_id` bigint unsigned NOT NULL,
  `receiver_role_code` varchar(64) DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` varchar(1024) NOT NULL DEFAULT '',
  `type` varchar(64) NOT NULL,
  `biz_type` varchar(64) DEFAULT NULL,
  `biz_id` bigint unsigned DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_receiver_read` (`receiver_user_id`,`is_read`,`created_at`),
  KEY `idx_notification_type` (`type`),
  KEY `idx_notification_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_review_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `reviewer_user_id` bigint unsigned NOT NULL,
  `reviewer_role_code` varchar(64) NOT NULL,
  `review_type` varchar(64) NOT NULL,
  `biz_type` varchar(64) NOT NULL,
  `biz_id` bigint unsigned DEFAULT NULL,
  `biz_title` varchar(255) NOT NULL DEFAULT '',
  `target_user_id` bigint unsigned DEFAULT NULL,
  `target_name` varchar(128) DEFAULT '',
  `action` varchar(32) NOT NULL,
  `remark` varchar(512) DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_review_record_reviewer` (`reviewer_user_id`,`review_type`,`created_at`),
  KEY `idx_review_record_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_ai_review_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `activity_id` bigint unsigned NOT NULL,
  `task_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `suggestion` varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  `risk_level` varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  `score` int DEFAULT NULL,
  `problems_json` text,
  `missing_fields_json` text,
  `similar_activities_json` text,
  `similarity_analysis` varchar(1024) DEFAULT NULL,
  `review_comment` varchar(2048) DEFAULT NULL,
  `model_name` varchar(128) DEFAULT NULL,
  `prompt_version` varchar(64) NOT NULL DEFAULT 'v1',
  `raw_response` text,
  `parse_status` varchar(32) NOT NULL DEFAULT 'PARSED',
  `error_message` varchar(1024) DEFAULT NULL,
  `manual_review_result` varchar(32) DEFAULT NULL,
  `manual_review_remark` varchar(512) DEFAULT NULL,
  `manual_reviewed_at` datetime DEFAULT NULL,
  `is_ai_adopted` tinyint(1) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_review_activity_prompt` (`activity_id`,`prompt_version`),
  KEY `idx_ai_review_status` (`task_status`,`updated_at`),
  KEY `idx_ai_review_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sys_role` (`role_code`, `role_name`, `description`, `status`) VALUES
('USER', '普通用户', '可浏览活动、报名、查看个人报名与签到状态', 1),
('ACTIVITY_ADMIN', '活动管理员', '可发起活动、查看报名名单、执行签到核销', 1),
('PLATFORM_ADMIN', '平台管理员', '可做平台级活动审核、主办方申请审核和活动治理', 1)
ON DUPLICATE KEY UPDATE
`role_name` = VALUES(`role_name`),
`description` = VALUES(`description`),
`status` = VALUES(`status`);

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `path`, `description`, `status`) VALUES
('activity:view', '查看活动', 'API', '/activity/public/*', '活动浏览', 1),
('activity:search', '搜索活动', 'API', '/activity/public/list', '活动搜索', 1),
('activity:create', '发起活动', 'API', '/activity', '创建活动', 1),
('activity:update', '编辑活动', 'API', '/activity', '编辑自己创建的活动', 1),
('activity:approve', '审核活动', 'API', NULL, '审核活动发布与下架申请', 1),
('activity:offline', '下架活动', 'API', NULL, '强制下架已发布活动', 1),
('activity:manage_category', '管理活动分类', 'API', NULL, '活动分类治理', 1),
('registration:create', '报名活动', 'API', '/activity/{id}/register', '创建报名', 1),
('registration:cancel', '退出报名', 'API', '/activity/{id}/register', '取消报名', 1),
('registration:view_self', '查看自己的报名', 'API', '/activity/registration/mine', '查看个人报名', 1),
('registration:view_all', '查看活动报名名单', 'API', '/activity/manage/{id}/registrations', '查看活动报名名单', 1),
('registration:export', '导出报名名单', 'API', NULL, '导出报名名单', 1),
('checkin:view_self', '查看自己的签到状态', 'API', '/activity/{id}/register/status', '查看个人签到凭证和状态', 1),
('checkin:open', '开启签到', 'API', NULL, '活动签到配置', 1),
('checkin:close', '关闭签到', 'API', NULL, '关闭签到', 1),
('checkin:verify', '核销签到', 'API', '/activity/manage/{id}/check-in/verify', '执行签到核销', 1),
('checkin:view_records', '查看签到记录', 'API', '/activity/manage/{id}/check-in/*', '查看签到统计与记录', 1),
('checkin:export', '导出签到名单', 'API', NULL, '导出签到记录', 1),
('notification:view_self', '查看个人通知', 'API', NULL, '查看个人通知', 1),
('notification:send_activity', '发送活动通知', 'API', NULL, '发送活动通知', 1),
('notification:publish_platform', '发布平台公告', 'API', NULL, '发布平台公告', 1),
('platform:user_manage', '管理用户', 'API', NULL, '平台用户治理', 1),
('platform:role_assign', '分配角色', 'API', NULL, '角色绑定与调整', 1),
('platform:statistics:view', '查看平台统计', 'API', NULL, '平台统计查看', 1),
('platform:auditlog:view', '查看审计日志', 'API', NULL, '查看审计日志', 1)
ON DUPLICATE KEY UPDATE
`permission_name` = VALUES(`permission_name`),
	`permission_type` = VALUES(`permission_type`),
	`path` = VALUES(`path`),
	`description` = VALUES(`description`),
	`status` = VALUES(`status`);

INSERT INTO `tb_user` (`phone`, `username`, `password`, `nick_name`, `icon`, `role_type`)
SELECT '10000000001', 'admin', '123456', '平台管理员', '', 2
WHERE NOT EXISTS (SELECT 1 FROM `tb_user` WHERE `username` = 'admin');

INSERT INTO `tb_user` (`phone`, `username`, `password`, `nick_name`, `icon`, `role_type`)
SELECT '10000000003', 'test', '123456', '西电活动中心', '', 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_user` WHERE `username` = 'test');

INSERT INTO `tb_user_info` (`user_id`, `city`, `college`, `grade`, `mentor`, `introduce`, `gender`, `birthday`)
SELECT u.`id`, '西安', '党委学生工作部', '2026', '', '负责校园大型活动、志愿服务和文化项目组织。', 0, '2000-01-01'
FROM `tb_user` u
LEFT JOIN `tb_user_info` ui ON ui.`user_id` = u.`id`
WHERE u.`username` = 'test'
  AND ui.`user_id` IS NULL;

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `tb_user` u
JOIN `sys_role` r ON r.`role_code` = 'PLATFORM_ADMIN'
WHERE u.`username` = 'admin';

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `tb_user` u
JOIN `sys_role` r ON r.`role_code` = 'ACTIVITY_ADMIN'
WHERE u.`username` = 'test';

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `tb_user` u
JOIN `sys_role` r ON r.`role_code` = 'USER'
WHERE u.`username` = 'test';

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
WHERE (
  r.`role_code` = 'USER'
  AND p.`permission_code` IN (
    'activity:view',
    'activity:search',
    'registration:create',
    'registration:cancel',
    'registration:view_self',
    'checkin:view_self',
    'notification:view_self'
  )
) OR (
  r.`role_code` = 'ACTIVITY_ADMIN'
  AND p.`permission_code` IN (
    'activity:view',
    'activity:search',
    'activity:create',
    'activity:update',
    'registration:view_all',
    'registration:export',
    'checkin:open',
    'checkin:close',
    'checkin:verify',
    'checkin:view_records',
    'checkin:export',
    'notification:send_activity'
  )
) OR (
  r.`role_code` = 'PLATFORM_ADMIN'
  AND p.`permission_code` IN (
    'activity:view',
    'activity:approve',
    'activity:offline',
    'activity:manage_category',
    'platform:user_manage',
    'platform:role_assign',
    'platform:statistics:view',
    'platform:auditlog:view',
    'notification:publish_platform'
  )
);

INSERT INTO `tb_activity_category` (`name`, `sort_no`, `status`) VALUES
('学术讲座', 1, 1),
('就业指导', 2, 1),
('竞赛训练', 3, 1),
('创新实践', 4, 1),
('文艺活动', 5, 1),
('体育活动', 6, 1),
('志愿公益', 7, 1),
('社团活动', 8, 1)
ON DUPLICATE KEY UPDATE `sort_no` = VALUES(`sort_no`), `status` = VALUES(`status`);

DELETE upt
FROM `tb_user_preference_tag` upt
JOIN `tb_activity_tag` t ON t.`id` = upt.`tag_id`
JOIN `tb_activity_category` c ON c.`id` = t.`category_id`
JOIN (
  SELECT '学术讲座' AS category_name, '前沿分享' AS tag_name UNION ALL
  SELECT '学术讲座', '名师讲堂' UNION ALL
  SELECT '学术讲座', '学科论坛' UNION ALL
  SELECT '学术讲座', '读书交流' UNION ALL
  SELECT '学术讲座', '学术沙龙' UNION ALL
  SELECT '就业指导', '求职训练' UNION ALL
  SELECT '就业指导', '简历面试' UNION ALL
  SELECT '就业指导', '实习辅导' UNION ALL
  SELECT '竞赛训练', '辩论训练' UNION ALL
  SELECT '竞赛训练', '赛前集训' UNION ALL
  SELECT '创新实践', '创客实践' UNION ALL
  SELECT '创新实践', '实验开放' UNION ALL
  SELECT '创新实践', '产学协作' UNION ALL
  SELECT '文艺活动', '音乐演出' UNION ALL
  SELECT '文艺活动', '戏剧展演' UNION ALL
  SELECT '文艺活动', '影像放映' UNION ALL
  SELECT '文艺活动', '美育工作坊' UNION ALL
  SELECT '文艺活动', '节庆晚会' UNION ALL
  SELECT '体育活动', '球类赛事' UNION ALL
  SELECT '体育活动', '跑步健身' UNION ALL
  SELECT '体育活动', '户外拓展' UNION ALL
  SELECT '体育活动', '体育训练' UNION ALL
  SELECT '志愿公益', '助学帮扶' UNION ALL
  SELECT '志愿公益', '大型赛会志愿' UNION ALL
  SELECT '社团活动', '骨干培训' UNION ALL
  SELECT '社团活动', '主题交流' UNION ALL
  SELECT '社团活动', '联谊活动' UNION ALL
  SELECT '社团活动', '例会沙龙'
) legacy ON legacy.category_name = c.`name` AND legacy.tag_name = t.`name`;

DELETE r
FROM `tb_activity_tag_relation` r
JOIN `tb_activity_tag` t ON t.`id` = r.`tag_id`
JOIN `tb_activity_category` c ON c.`id` = t.`category_id`
JOIN (
  SELECT '学术讲座' AS category_name, '前沿分享' AS tag_name UNION ALL
  SELECT '学术讲座', '名师讲堂' UNION ALL
  SELECT '学术讲座', '学科论坛' UNION ALL
  SELECT '学术讲座', '读书交流' UNION ALL
  SELECT '学术讲座', '学术沙龙' UNION ALL
  SELECT '就业指导', '求职训练' UNION ALL
  SELECT '就业指导', '简历面试' UNION ALL
  SELECT '就业指导', '实习辅导' UNION ALL
  SELECT '竞赛训练', '辩论训练' UNION ALL
  SELECT '竞赛训练', '赛前集训' UNION ALL
  SELECT '创新实践', '创客实践' UNION ALL
  SELECT '创新实践', '实验开放' UNION ALL
  SELECT '创新实践', '产学协作' UNION ALL
  SELECT '文艺活动', '音乐演出' UNION ALL
  SELECT '文艺活动', '戏剧展演' UNION ALL
  SELECT '文艺活动', '影像放映' UNION ALL
  SELECT '文艺活动', '美育工作坊' UNION ALL
  SELECT '文艺活动', '节庆晚会' UNION ALL
  SELECT '体育活动', '球类赛事' UNION ALL
  SELECT '体育活动', '跑步健身' UNION ALL
  SELECT '体育活动', '户外拓展' UNION ALL
  SELECT '体育活动', '体育训练' UNION ALL
  SELECT '志愿公益', '助学帮扶' UNION ALL
  SELECT '志愿公益', '大型赛会志愿' UNION ALL
  SELECT '社团活动', '骨干培训' UNION ALL
  SELECT '社团活动', '主题交流' UNION ALL
  SELECT '社团活动', '联谊活动' UNION ALL
  SELECT '社团活动', '例会沙龙'
) legacy ON legacy.category_name = c.`name` AND legacy.tag_name = t.`name`;

DELETE t
FROM `tb_activity_tag` t
JOIN `tb_activity_category` c ON c.`id` = t.`category_id`
JOIN (
  SELECT '学术讲座' AS category_name, '前沿分享' AS tag_name UNION ALL
  SELECT '学术讲座', '名师讲堂' UNION ALL
  SELECT '学术讲座', '学科论坛' UNION ALL
  SELECT '学术讲座', '读书交流' UNION ALL
  SELECT '学术讲座', '学术沙龙' UNION ALL
  SELECT '就业指导', '求职训练' UNION ALL
  SELECT '就业指导', '简历面试' UNION ALL
  SELECT '就业指导', '实习辅导' UNION ALL
  SELECT '竞赛训练', '辩论训练' UNION ALL
  SELECT '竞赛训练', '赛前集训' UNION ALL
  SELECT '创新实践', '创客实践' UNION ALL
  SELECT '创新实践', '实验开放' UNION ALL
  SELECT '创新实践', '产学协作' UNION ALL
  SELECT '文艺活动', '音乐演出' UNION ALL
  SELECT '文艺活动', '戏剧展演' UNION ALL
  SELECT '文艺活动', '影像放映' UNION ALL
  SELECT '文艺活动', '美育工作坊' UNION ALL
  SELECT '文艺活动', '节庆晚会' UNION ALL
  SELECT '体育活动', '球类赛事' UNION ALL
  SELECT '体育活动', '跑步健身' UNION ALL
  SELECT '体育活动', '户外拓展' UNION ALL
  SELECT '体育活动', '体育训练' UNION ALL
  SELECT '志愿公益', '助学帮扶' UNION ALL
  SELECT '志愿公益', '大型赛会志愿' UNION ALL
  SELECT '社团活动', '骨干培训' UNION ALL
  SELECT '社团活动', '主题交流' UNION ALL
  SELECT '社团活动', '联谊活动' UNION ALL
  SELECT '社团活动', '例会沙龙'
) legacy ON legacy.category_name = c.`name` AND legacy.tag_name = t.`name`;

INSERT INTO `tb_activity_tag` (`category_id`, `name`, `sort_no`, `status`)
SELECT c.`id`, t.`name`, t.`sort_no`, 1
FROM `tb_activity_category` c
JOIN (
  SELECT '学术讲座' AS category_name, '名师讲座' AS name, 1 AS sort_no UNION ALL
  SELECT '学术讲座', '学术报告', 2 UNION ALL
  SELECT '学术讲座', '科研交流', 3 UNION ALL
  SELECT '学术讲座', '专业分享', 4 UNION ALL
  SELECT '学术讲座', '论文写作', 5 UNION ALL
  SELECT '学术讲座', '保研经验', 6 UNION ALL
  SELECT '学术讲座', '考研经验', 7 UNION ALL
  SELECT '学术讲座', '学科前沿', 8 UNION ALL
  SELECT '学术讲座', '实验室开放', 9 UNION ALL
  SELECT '学术讲座', '导师面对面', 10 UNION ALL
  SELECT '就业指导', '实习', 1 UNION ALL
  SELECT '就业指导', '秋招', 2 UNION ALL
  SELECT '就业指导', '春招', 3 UNION ALL
  SELECT '就业指导', '简历指导', 4 UNION ALL
  SELECT '就业指导', '面试经验', 5 UNION ALL
  SELECT '就业指导', '职业规划', 6 UNION ALL
  SELECT '就业指导', '企业宣讲', 7 UNION ALL
  SELECT '就业指导', '就业分享', 8 UNION ALL
  SELECT '就业指导', '求职技巧', 9 UNION ALL
  SELECT '就业指导', '职场能力', 10 UNION ALL
  SELECT '就业指导', 'Java后端', 11 UNION ALL
  SELECT '就业指导', '前端开发', 12 UNION ALL
  SELECT '就业指导', '人工智能', 13 UNION ALL
  SELECT '就业指导', '产品运营', 14 UNION ALL
  SELECT '竞赛训练', '学科竞赛', 1 UNION ALL
  SELECT '竞赛训练', '编程竞赛', 2 UNION ALL
  SELECT '竞赛训练', '数学建模', 3 UNION ALL
  SELECT '竞赛训练', '蓝桥杯', 4 UNION ALL
  SELECT '竞赛训练', 'ACM训练', 5 UNION ALL
  SELECT '竞赛训练', '挑战杯', 6 UNION ALL
  SELECT '竞赛训练', '互联网+', 7 UNION ALL
  SELECT '竞赛训练', '电子设计', 8 UNION ALL
  SELECT '竞赛训练', '机器人竞赛', 9 UNION ALL
  SELECT '竞赛训练', '英语竞赛', 10 UNION ALL
  SELECT '竞赛训练', '创新创业竞赛', 11 UNION ALL
  SELECT '竞赛训练', '赛前培训', 12 UNION ALL
  SELECT '竞赛训练', '组队招募', 13 UNION ALL
  SELECT '创新实践', '创新项目', 1 UNION ALL
  SELECT '创新实践', '创业实践', 2 UNION ALL
  SELECT '创新实践', '科研训练', 3 UNION ALL
  SELECT '创新实践', '项目路演', 4 UNION ALL
  SELECT '创新实践', '实践课程', 5 UNION ALL
  SELECT '创新实践', '技术分享', 6 UNION ALL
  SELECT '创新实践', '创客活动', 7 UNION ALL
  SELECT '创新实践', '实验实践', 8 UNION ALL
  SELECT '创新实践', '产品设计', 9 UNION ALL
  SELECT '创新实践', '项目招募', 10 UNION ALL
  SELECT '创新实践', '创新工坊', 11 UNION ALL
  SELECT '创新实践', '校企合作', 12 UNION ALL
  SELECT '文艺活动', '校园歌手', 1 UNION ALL
  SELECT '文艺活动', '晚会演出', 2 UNION ALL
  SELECT '文艺活动', '舞蹈表演', 3 UNION ALL
  SELECT '文艺活动', '摄影展', 4 UNION ALL
  SELECT '文艺活动', '读书会', 5 UNION ALL
  SELECT '文艺活动', '电影放映', 6 UNION ALL
  SELECT '文艺活动', '音乐活动', 7 UNION ALL
  SELECT '文艺活动', '戏剧表演', 8 UNION ALL
  SELECT '文艺活动', '主持朗诵', 9 UNION ALL
  SELECT '文艺活动', '书画展览', 10 UNION ALL
  SELECT '文艺活动', '传统文化', 11 UNION ALL
  SELECT '文艺活动', '社交舞会', 12 UNION ALL
  SELECT '体育活动', '篮球', 1 UNION ALL
  SELECT '体育活动', '足球', 2 UNION ALL
  SELECT '体育活动', '羽毛球', 3 UNION ALL
  SELECT '体育活动', '乒乓球', 4 UNION ALL
  SELECT '体育活动', '排球', 5 UNION ALL
  SELECT '体育活动', '跑步打卡', 6 UNION ALL
  SELECT '体育活动', '运动会', 7 UNION ALL
  SELECT '体育活动', '健身训练', 8 UNION ALL
  SELECT '体育活动', '趣味运动', 9 UNION ALL
  SELECT '体育活动', '户外活动', 10 UNION ALL
  SELECT '体育活动', '体育竞赛', 11 UNION ALL
  SELECT '体育活动', '健康打卡', 12 UNION ALL
  SELECT '志愿公益', '志愿服务', 1 UNION ALL
  SELECT '志愿公益', '公益实践', 2 UNION ALL
  SELECT '志愿公益', '社区服务', 3 UNION ALL
  SELECT '志愿公益', '支教活动', 4 UNION ALL
  SELECT '志愿公益', '环保行动', 5 UNION ALL
  SELECT '志愿公益', '校园服务', 6 UNION ALL
  SELECT '志愿公益', '爱心捐助', 7 UNION ALL
  SELECT '志愿公益', '文明引导', 8 UNION ALL
  SELECT '志愿公益', '公益宣传', 9 UNION ALL
  SELECT '志愿公益', '社会实践', 10 UNION ALL
  SELECT '志愿公益', '应急志愿', 11 UNION ALL
  SELECT '志愿公益', '志愿者招募', 12 UNION ALL
  SELECT '社团活动', '社团招新', 1 UNION ALL
  SELECT '社团活动', '社团例会', 2 UNION ALL
  SELECT '社团活动', '社团开放日', 3 UNION ALL
  SELECT '社团活动', '兴趣小组', 4 UNION ALL
  SELECT '社团活动', '社团展示', 5 UNION ALL
  SELECT '社团活动', '社团培训', 6 UNION ALL
  SELECT '社团活动', '社团联谊', 7 UNION ALL
  SELECT '社团活动', '校园组织', 8 UNION ALL
  SELECT '社团活动', '学生会活动', 9 UNION ALL
  SELECT '社团活动', '协会活动', 10 UNION ALL
  SELECT '社团活动', '俱乐部活动', 11 UNION ALL
  SELECT '社团活动', '新生见面会', 12
) t ON t.category_name = c.`name`
ON DUPLICATE KEY UPDATE `sort_no` = VALUES(`sort_no`), `status` = VALUES(`status`);

CREATE TABLE IF NOT EXISTS `tb_user_profile_embedding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `profile_text` text NOT NULL COMMENT '用户画像文本',
  `embedding_vector` longtext NOT NULL COMMENT '用户画像向量JSON',
  `model_name` varchar(100) NOT NULL COMMENT 'Embedding模型名称',
  `version` int NOT NULL DEFAULT 1 COMMENT '画像版本',
  `behavior_window_days` int NOT NULL DEFAULT 30 COMMENT '行为统计窗口天数',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1有效，2失效',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_model_version` (`user_id`, `model_name`, `version`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像向量表';

CREATE TABLE IF NOT EXISTS `tb_embedding_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type` varchar(50) NOT NULL COMMENT '业务类型：ACTIVITY / USER_PROFILE',
  `biz_id` bigint NOT NULL COMMENT '业务ID',
  `task_status` tinyint NOT NULL DEFAULT 0 COMMENT '任务状态：0待处理，1处理中，2成功，3失败',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embedding_task_biz` (`biz_type`, `biz_id`),
  KEY `idx_task_status` (`task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Embedding生成任务表';

SET FOREIGN_KEY_CHECKS = 1;
