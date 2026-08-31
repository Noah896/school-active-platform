SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @db_name = DATABASE();

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'tb_activity_registration'
          AND COLUMN_NAME = 'request_id'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity_registration` ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT ''报名请求ID'' AFTER `status`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'tb_activity_registration'
          AND COLUMN_NAME = 'fail_reason'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity_registration` ADD COLUMN `fail_reason` varchar(255) DEFAULT NULL COMMENT ''失败原因'' AFTER `request_id`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name
          AND TABLE_NAME = 'tb_activity_registration'
          AND COLUMN_NAME = 'confirm_time'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity_registration` ADD COLUMN `confirm_time` datetime DEFAULT NULL COMMENT ''最终确认时间'' AFTER `check_in_time`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `tb_activity_registration`
  MODIFY COLUMN `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '报名状态：0报名待审核，1报名通过，2报名驳回，3已退出，4退出待审核';

SET FOREIGN_KEY_CHECKS = 1;
