SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tb_activity'
        AND COLUMN_NAME = 'reviewer_id'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity` ADD COLUMN `reviewer_id` bigint unsigned DEFAULT NULL COMMENT ''审核人ID'' AFTER `status`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tb_activity'
        AND COLUMN_NAME = 'review_remark'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity` ADD COLUMN `review_remark` varchar(512) DEFAULT NULL COMMENT ''审核备注/驳回原因'' AFTER `reviewer_id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tb_activity'
        AND COLUMN_NAME = 'review_time'
    ),
    'SELECT 1',
    'ALTER TABLE `tb_activity` ADD COLUMN `review_time` datetime DEFAULT NULL COMMENT ''审核时间'' AFTER `review_remark`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
