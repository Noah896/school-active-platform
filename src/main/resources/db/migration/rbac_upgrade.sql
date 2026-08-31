-- 正式 RBAC 升级脚本
-- 目标：在保留 tb_user.role_type 兼容字段的同时，引入用户-角色-权限三层模型

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `user_id` bigint unsigned NOT NULL,
    `role_id` bigint unsigned NOT NULL,
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_role` (`user_id`,`role_id`),
    KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `role_id` bigint unsigned NOT NULL,
    `permission_id` bigint unsigned NOT NULL,
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_permission` (`role_id`,`permission_id`),
    KEY `idx_sys_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

INSERT INTO `sys_role` (`role_code`, `role_name`, `description`, `status`) VALUES
('USER', '普通用户', '可浏览活动、报名、查看个人报名与签到状态', 1),
('ACTIVITY_ADMIN', '活动管理员', '可发起活动、查看报名名单、执行签到核销', 1),
('PLATFORM_ADMIN', '平台管理员', '可做平台级用户、角色、统计与审计管理', 1)
ON DUPLICATE KEY UPDATE
`role_name` = VALUES(`role_name`),
`description` = VALUES(`description`),
`status` = VALUES(`status`);

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `permission_type`, `path`, `description`, `status`) VALUES
('activity:view', '查看活动', 'API', '/activity/public/*', '活动浏览', 1),
('activity:search', '搜索活动', 'API', '/activity/public/list', '活动搜索', 1),
('activity:create', '发起活动', 'API', '/activity', '创建活动', 1),
('activity:update', '编辑活动', 'API', '/activity', '编辑自己创建的活动', 1),
('activity:approve', '审核活动', 'API', NULL, '审核活动', 1),
('activity:offline', '下架活动', 'API', NULL, '下架活动', 1),
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

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `tb_user` u
JOIN `sys_role` r ON r.`role_code` = 'USER'
LEFT JOIN `sys_user_role` ur ON ur.`user_id` = u.`id` AND ur.`role_id` = r.`id`
WHERE u.`role_type` = 2
  AND ur.`id` IS NULL;

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `tb_user` u
JOIN `sys_role` r ON r.`role_code` = 'ACTIVITY_ADMIN'
LEFT JOIN `sys_user_role` ur ON ur.`user_id` = u.`id` AND ur.`role_id` = r.`id`
WHERE u.`role_type` = 1
  AND ur.`id` IS NULL;

-- 平台管理员请按实际账号手工赋权，例如：
-- INSERT INTO sys_user_role (user_id, role_id)
-- SELECT 1, id FROM sys_role WHERE role_code = 'PLATFORM_ADMIN';
