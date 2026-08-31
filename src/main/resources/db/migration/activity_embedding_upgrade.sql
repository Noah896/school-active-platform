CREATE TABLE IF NOT EXISTS `tb_user_profile_embedding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `profile_text` text NOT NULL COMMENT '用户画像文本',
  `embedding_vector` longtext NOT NULL COMMENT '用户画像向量JSON',
  `model_name` varchar(100) NOT NULL COMMENT 'Embedding模型名称',
  `version` int NOT NULL DEFAULT 1 COMMENT '画像版本',
  `behavior_window_days` int NOT NULL DEFAULT 30 COMMENT '行为统计窗口天数',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1有效，2失效',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_model_version` (`user_id`, `model_name`, `version`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像向量表';

CREATE TABLE IF NOT EXISTS `tb_embedding_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type` varchar(50) NOT NULL COMMENT '业务类型：ACTIVITY / USER_PROFILE',
  `biz_id` bigint NOT NULL COMMENT '业务ID',
  `task_status` tinyint NOT NULL DEFAULT 0 COMMENT '任务状态：0待处理，1处理中，2成功，3失败',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embedding_task_biz` (`biz_type`, `biz_id`),
  KEY `idx_task_status` (`task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Embedding生成任务表';
