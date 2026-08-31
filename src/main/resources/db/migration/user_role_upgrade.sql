-- 用户身份权限升级脚本（主办方 / 学生）
-- 注意：请先确认数据库为 campus，并已存在 tb_user 表

-- 1) 给 tb_user 增加账号类型字段
ALTER TABLE `tb_user`
    ADD COLUMN `role_type` tinyint(1) NOT NULL DEFAULT 2 COMMENT '账号类型：1主办方，2学生' AFTER `icon`;

-- 2) 初始化历史账号默认角色为学生
UPDATE `tb_user`
SET `role_type` = 2
WHERE `role_type` IS NULL OR `role_type` NOT IN (1, 2);

-- 3) 如需指定主办方账号，可手工执行类似语句
-- UPDATE `tb_user` SET `role_type` = 1 WHERE `id` IN (1);
