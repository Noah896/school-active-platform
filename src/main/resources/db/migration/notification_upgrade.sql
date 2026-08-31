SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `sys_notification` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `receiver_user_id` bigint unsigned NOT NULL COMMENT '接收用户ID',
  `receiver_role_code` varchar(64) DEFAULT NULL COMMENT '接收角色编码',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` varchar(1024) NOT NULL DEFAULT '' COMMENT '通知内容',
  `type` varchar(64) NOT NULL COMMENT '通知类型',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '业务类型',
  `biz_id` bigint unsigned DEFAULT NULL COMMENT '业务ID',
  `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_time` datetime DEFAULT NULL COMMENT '已读时间',
  PRIMARY KEY (`id`),
  KEY `idx_notification_receiver_read` (`receiver_user_id`,`is_read`,`created_at`),
  KEY `idx_notification_type` (`type`),
  KEY `idx_notification_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';

SET FOREIGN_KEY_CHECKS = 1;
