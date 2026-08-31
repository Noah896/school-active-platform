SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `tb_activity_registration`
    ADD COLUMN `voucher_id` bigint(20) unsigned DEFAULT NULL COMMENT '签到凭证ID' AFTER `status`;

ALTER TABLE `tb_activity_registration`
    ADD KEY `idx_registration_voucher` (`voucher_id`);

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
