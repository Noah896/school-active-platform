ALTER TABLE `tb_activity`
  ADD COLUMN `activity_flow` text COMMENT '活动流程' AFTER `content`,
  ADD COLUMN `faq` text COMMENT '常见问题' AFTER `activity_flow`;

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
