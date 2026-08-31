CREATE TABLE IF NOT EXISTS `tb_activity_post` (
  `id` bigint unsigned NOT NULL,
  `activity_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `content` varchar(1000) NOT NULL,
  `visibility` tinyint(1) NOT NULL DEFAULT 1 COMMENT '可见性：1公开',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1正常 2已删除 3已隐藏',
  `like_count` int NOT NULL DEFAULT 0,
  `comment_count` int NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_post_activity` (`activity_id`,`status`,`create_time`),
  KEY `idx_activity_post_user` (`user_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态表';

CREATE TABLE IF NOT EXISTS `tb_activity_post_image` (
  `id` bigint unsigned NOT NULL,
  `post_id` bigint unsigned NOT NULL,
  `image_url` varchar(1024) NOT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_post_image_post` (`post_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态图片表';

CREATE TABLE IF NOT EXISTS `tb_activity_post_like` (
  `id` bigint unsigned NOT NULL,
  `post_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_post_like_post_user` (`post_id`,`user_id`),
  KEY `idx_activity_post_like_user` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态点赞表';

CREATE TABLE IF NOT EXISTS `tb_activity_post_comment` (
  `id` bigint unsigned NOT NULL,
  `post_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `content` varchar(200) NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1正常 2已删除 3已隐藏',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_activity_post_comment_post` (`post_id`,`status`,`create_time`),
  KEY `idx_activity_post_comment_user` (`user_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园圈动态评论表';
