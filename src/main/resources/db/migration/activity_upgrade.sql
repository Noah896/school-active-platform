SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `tb_activity` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `creator_id` bigint(20) unsigned NOT NULL COMMENT '创建人用户ID',
  `organizer_name` varchar(128) NOT NULL COMMENT '主办方名称',
  `title` varchar(255) NOT NULL COMMENT '活动标题',
  `cover_image` varchar(1024) DEFAULT NULL COMMENT '封面图',
  `images` varchar(4096) DEFAULT NULL COMMENT '活动图片，多个以逗号分隔',
  `summary` varchar(512) DEFAULT NULL COMMENT '活动摘要',
  `content` text COMMENT '活动详情',
  `category` varchar(64) NOT NULL COMMENT '活动分类',
  `custom_category` varchar(64) DEFAULT NULL COMMENT '自定义活动类型，仅 category=其他 时使用',
  `registration_mode` varchar(64) NOT NULL DEFAULT 'AUDIT_REQUIRED' COMMENT '报名模式：AUDIT_REQUIRED/FIRST_COME_FIRST_SERVED',
  `contact_info` varchar(255) DEFAULT NULL COMMENT '主办方联系方式',
  `location` varchar(255) NOT NULL COMMENT '活动地点',
  `max_participants` int(11) NOT NULL COMMENT '报名人数上限',
  `registered_count` int(11) NOT NULL DEFAULT 0 COMMENT '已报名人数',
  `registration_start_time` datetime NOT NULL COMMENT '报名开始时间',
  `registration_end_time` datetime NOT NULL COMMENT '报名结束时间',
  `event_start_time` datetime NOT NULL COMMENT '活动开始时间',
  `event_end_time` datetime NOT NULL COMMENT '活动结束时间',
  `check_in_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用签到',
  `check_in_code` varchar(64) DEFAULT NULL COMMENT '签到码',
  `check_in_code_expire_time` datetime DEFAULT NULL COMMENT '签到码过期时间',
  `status` tinyint(1) NOT NULL DEFAULT 2 COMMENT '状态：1待审核，2已发布，3已驳回，4强制下架，5下架待审核',
  `reviewer_id` bigint(20) unsigned DEFAULT NULL COMMENT '审核人ID',
  `review_remark` varchar(512) DEFAULT NULL COMMENT '审核备注/驳回原因',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity_creator` (`creator_id`),
  KEY `idx_activity_category` (`category`),
  KEY `idx_activity_event_start` (`event_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

CREATE TABLE IF NOT EXISTS `tb_activity_registration` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint(20) unsigned NOT NULL COMMENT '活动ID',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户ID',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '报名状态：0报名待审核，1报名通过，2报名驳回，3已退出，4退出待审核',
  `request_id` varchar(64) DEFAULT NULL COMMENT '报名请求ID',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `voucher_id` bigint(20) unsigned DEFAULT NULL COMMENT '签到凭证ID',
  `check_in_status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '签到状态：0未签到，1已签到',
  `check_in_time` datetime DEFAULT NULL COMMENT '签到时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '最终确认时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`,`user_id`),
  KEY `idx_registration_voucher` (`voucher_id`),
  KEY `idx_registration_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名表';

CREATE TABLE IF NOT EXISTS `tb_activity_voucher` (
  `id` bigint(20) unsigned NOT NULL COMMENT '凭证ID',
  `display_code` varchar(32) NOT NULL COMMENT '展示码',
  `activity_id` bigint(20) unsigned NOT NULL COMMENT '活动ID',
  `registration_id` bigint(20) unsigned NOT NULL COMMENT '报名记录ID',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '报名用户ID',
  `status` varchar(32) NOT NULL DEFAULT 'UNUSED' COMMENT '凭证状态：UNUSED/CHECKED_IN/CANCELED/EXPIRED',
  `issued_time` datetime NOT NULL COMMENT '签发时间',
  `checked_in_time` datetime DEFAULT NULL COMMENT '核销签到时间',
  `checked_in_by` bigint(20) unsigned DEFAULT NULL COMMENT '核销操作人ID',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voucher_display_code` (`display_code`),
  UNIQUE KEY `uk_voucher_registration` (`registration_id`),
  KEY `idx_voucher_activity_user` (`activity_id`,`user_id`),
  KEY `idx_voucher_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动签到凭证表';

CREATE TABLE IF NOT EXISTS `tb_activity_check_in_record` (
  `id` bigint(20) unsigned NOT NULL COMMENT '主键',
  `activity_id` bigint(20) unsigned NOT NULL COMMENT '活动ID',
  `voucher_id` bigint(20) unsigned DEFAULT NULL COMMENT '凭证ID',
  `user_id` bigint(20) unsigned DEFAULT NULL COMMENT '报名用户ID',
  `operator_id` bigint(20) unsigned NOT NULL COMMENT '核销操作人ID',
  `result_status` varchar(64) NOT NULL COMMENT '核销结果',
  `request_key` varchar(128) DEFAULT NULL COMMENT '幂等请求Key',
  `request_fingerprint` varchar(128) DEFAULT NULL COMMENT '请求指纹',
  `response_body` varchar(2048) DEFAULT NULL COMMENT '响应摘要',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_check_in_record_activity` (`activity_id`),
  KEY `idx_check_in_record_voucher` (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动签到核销记录表';

SET FOREIGN_KEY_CHECKS = 1;
