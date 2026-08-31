-- 活动图片升级脚本（多图上传 + 首图封面）
-- 注意：请先确认数据库为 campus，并已存在 tb_activity 表

ALTER TABLE `tb_activity`
    ADD COLUMN `images` varchar(4096) DEFAULT NULL COMMENT '活动图片，多个以逗号分隔' AFTER `cover_image`;

UPDATE `tb_activity`
SET `images` = `cover_image`
WHERE (`images` IS NULL OR `images` = '')
  AND `cover_image` IS NOT NULL
  AND `cover_image` <> '';
