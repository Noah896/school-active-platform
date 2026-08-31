-- 用户资料扩展升级脚本（校园活动平台）
-- 注意：请先确认数据库为 campus，并已存在 tb_user / tb_user_info 表

-- 1) 扩展用户资料表 tb_user_info：学院 / 年级 / 导师
ALTER TABLE `tb_user_info`
    ADD COLUMN `college` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '所属学院' AFTER `city`,
    ADD COLUMN `grade` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '所属年级' AFTER `college`,
    ADD COLUMN `mentor` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '导师' AFTER `grade`;

-- 2) 可选：头像 URL 可能较长，可将 tb_user.icon 扩到 512
-- ALTER TABLE `tb_user` MODIFY COLUMN `icon` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '人物头像';

