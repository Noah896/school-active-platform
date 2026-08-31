SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `sys_review_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
  `reviewer_user_id` bigint unsigned NOT NULL COMMENT '审核人用户ID',
  `reviewer_role_code` varchar(64) NOT NULL COMMENT '审核人角色',
  `review_type` varchar(64) NOT NULL COMMENT '审核类型：ACTIVITY_ADMIN/PLATFORM_ADMIN',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` bigint unsigned DEFAULT NULL COMMENT '业务ID',
  `biz_title` varchar(255) NOT NULL DEFAULT '' COMMENT '业务标题',
  `target_user_id` bigint unsigned DEFAULT NULL COMMENT '被审核用户ID',
  `target_name` varchar(128) DEFAULT '' COMMENT '被审核对象名称',
  `action` varchar(32) NOT NULL COMMENT '审核动作：APPROVED/REJECTED',
  `remark` varchar(512) DEFAULT '' COMMENT '审核备注',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_record_reviewer` (`reviewer_user_id`,`review_type`,`created_at`),
  KEY `idx_review_record_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核历史记录表';

SET FOREIGN_KEY_CHECKS = 1;
