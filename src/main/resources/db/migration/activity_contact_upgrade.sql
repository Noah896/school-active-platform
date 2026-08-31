SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tb_activity'
        AND COLUMN_NAME = 'contact_info'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity` ADD COLUMN `contact_info` varchar(255) DEFAULT NULL COMMENT ''主办方联系方式'' AFTER `registration_mode`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
