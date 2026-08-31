ALTER TABLE `tb_activity`
  ADD COLUMN `registration_mode` varchar(64) NOT NULL DEFAULT 'AUDIT_REQUIRED' COMMENT '报名模式：AUDIT_REQUIRED/FIRST_COME_FIRST_SERVED' AFTER `category`;

UPDATE `tb_activity`
SET `registration_mode` = 'AUDIT_REQUIRED'
WHERE `registration_mode` IS NULL OR `registration_mode` = '';
