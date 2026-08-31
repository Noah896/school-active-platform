SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tb_activity'
        AND COLUMN_NAME = 'custom_category'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity` ADD COLUMN `custom_category` varchar(64) DEFAULT NULL COMMENT ''自定义活动类型，仅 category=其他 时使用'' AFTER `category`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
