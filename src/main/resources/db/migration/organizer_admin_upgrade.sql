-- 主办方申请与平台管理员账号升级脚本

-- 如果当前库还没有 username 字段，请手工执行：
-- ALTER TABLE `tb_user` ADD COLUMN `username` varchar(64) DEFAULT NULL AFTER `phone`;
-- ALTER TABLE `tb_user` ADD UNIQUE KEY `uk_tb_user_username` (`username`);

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主办方申请表';
