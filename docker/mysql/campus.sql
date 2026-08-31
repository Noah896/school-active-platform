-- MySQL dump for campus activity platform
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `tb_activity`;
CREATE TABLE `tb_activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `creator_id` bigint unsigned NOT NULL COMMENT '创建人用户ID',
  `organizer_name` varchar(128) NOT NULL COMMENT '主办方名称',
  `title` varchar(255) NOT NULL COMMENT '活动标题',
  `cover_image` varchar(1024) DEFAULT NULL COMMENT '封面图',
  `images` varchar(4096) DEFAULT NULL COMMENT '活动图片，多个以逗号分隔',
  `summary` varchar(512) DEFAULT NULL COMMENT '活动摘要',
  `content` text COMMENT '活动详情',
  `category` varchar(64) NOT NULL COMMENT '活动分类',
  `location` varchar(255) NOT NULL COMMENT '活动地点',
  `max_participants` int NOT NULL COMMENT '报名人数上限',
  `registered_count` int NOT NULL DEFAULT '0' COMMENT '已报名人数',
  `registration_start_time` datetime NOT NULL COMMENT '报名开始时间',
  `registration_end_time` datetime NOT NULL COMMENT '报名结束时间',
  `event_start_time` datetime NOT NULL COMMENT '活动开始时间',
  `event_end_time` datetime NOT NULL COMMENT '活动结束时间',
  `check_in_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用签到',
  `check_in_code` varchar(64) DEFAULT NULL COMMENT '签到码',
  `check_in_code_expire_time` datetime DEFAULT NULL COMMENT '签到码过期时间',
  `status` tinyint(1) NOT NULL DEFAULT '2' COMMENT '状态：1草稿，2已发布，3已结束',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity_creator` (`creator_id`),
  KEY `idx_activity_category` (`category`),
  KEY `idx_activity_event_start` (`event_start_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

INSERT INTO `tb_activity` VALUES
(1,1,'西电校园活动中心','2026 春季校园创客开放日','https://images.unsplash.com/photo-1511578314322-379afb476865?auto=format&fit=crop&w=1200&q=80','https://images.unsplash.com/photo-1511578314322-379afb476865?auto=format&fit=crop&w=1200&q=80','面向全校开放的创客体验日，包含项目展示、社团互动与报名咨询。','欢迎各学院同学参与创客开放日，现场可体验社团作品、了解大型活动组织流程，并领取后续活动报名指引。','开放日','大学生活动中心一楼大厅',200,0,'2026-04-18 09:00:00','2026-04-27 18:00:00','2026-04-28 14:00:00','2026-04-28 18:00:00',0,NULL,NULL,2,'2026-04-21 08:08:04','2026-04-22 11:06:08'),
(2,1,'就业与实践服务站','大型招聘会志愿者招募','https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1200&q=80','https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1200&q=80','面向校园大型招聘会招募现场志愿者，支持报名与现场签到。','志愿者将协助签到引导、秩序维护和咨询答疑，适合希望参与大型活动组织的同学报名。','志愿服务','南校区体育馆',80,0,'2026-04-20 08:00:00','2026-04-25 20:00:00','2026-04-30 08:30:00','2026-04-30 18:00:00',1,NULL,NULL,2,'2026-04-21 08:08:04','2026-04-22 11:01:15'),
(3,1010,'西安电子科技大学','院士讲座','https://xud-campus.oss-cn-beijing.aliyuncs.com/activities/1010/20260421/aa9cee4b76b043e3aeb24dd4ae248c04.jpg','https://xud-campus.oss-cn-beijing.aliyuncs.com/activities/1010/20260421/aa9cee4b76b043e3aeb24dd4ae248c04.jpg,https://xud-campus.oss-cn-beijing.aliyuncs.com/activities/1010/20260421/2027165337e14eb5bd2119188bf452b5.jpg','能拿学分','','讲座','杭研院A10',100,1,'2026-04-19 19:28:00','2026-04-21 19:28:00','2026-04-21 19:28:00','2026-05-24 19:28:00',1,'432261','2026-04-21 22:29:23',2,'2026-04-21 11:29:18','2026-04-22 10:16:22');

DROP TABLE IF EXISTS `tb_activity_registration`;
CREATE TABLE `tb_activity_registration` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '报名状态：0待确认，1成功，2失败，3已取消',
  `request_id` varchar(64) DEFAULT NULL COMMENT '报名请求ID',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `voucher_id` bigint unsigned DEFAULT NULL COMMENT '签到凭证ID',
  `check_in_status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '签到状态：0未签到，1已签到',
  `check_in_time` datetime DEFAULT NULL COMMENT '签到时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '最终确认时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`,`user_id`),
  KEY `idx_registration_user` (`user_id`),
  KEY `idx_registration_voucher` (`voucher_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COMMENT='活动报名表';

INSERT INTO `tb_activity_registration` VALUES
(2,3,1012,1,NULL,NULL,583628425655222273,1,'2026-04-22 18:19:09',NULL,'2026-04-22 10:16:22','2026-04-22 10:19:09');

DROP TABLE IF EXISTS `tb_activity_voucher`;
CREATE TABLE `tb_activity_voucher` (
  `id` bigint unsigned NOT NULL COMMENT '凭证ID',
  `display_code` varchar(32) NOT NULL COMMENT '展示码',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `registration_id` bigint unsigned NOT NULL COMMENT '报名记录ID',
  `user_id` bigint unsigned NOT NULL COMMENT '报名用户ID',
  `status` varchar(32) NOT NULL DEFAULT 'UNUSED' COMMENT '凭证状态：UNUSED/CHECKED_IN/CANCELED/EXPIRED',
  `issued_time` datetime NOT NULL COMMENT '签发时间',
  `checked_in_time` datetime DEFAULT NULL COMMENT '核销签到时间',
  `checked_in_by` bigint unsigned DEFAULT NULL COMMENT '核销操作人ID',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voucher_display_code` (`display_code`),
  UNIQUE KEY `uk_voucher_registration` (`registration_id`),
  KEY `idx_voucher_activity_user` (`activity_id`,`user_id`),
  KEY `idx_voucher_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动签到凭证表';

INSERT INTO `tb_activity_voucher` VALUES
(583628425655222273,'4EPC4S4J',3,2,1012,'CHECKED_IN','2026-04-22 18:16:23','2026-04-22 18:19:09',1010,'2026-04-22 10:16:22','2026-04-22 10:19:09');

DROP TABLE IF EXISTS `tb_activity_check_in_record`;
CREATE TABLE `tb_activity_check_in_record` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `voucher_id` bigint unsigned DEFAULT NULL COMMENT '凭证ID',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '报名用户ID',
  `operator_id` bigint unsigned NOT NULL COMMENT '核销操作人ID',
  `result_status` varchar(64) NOT NULL COMMENT '核销结果',
  `request_key` varchar(128) DEFAULT NULL COMMENT '幂等请求Key',
  `request_fingerprint` varchar(128) DEFAULT NULL COMMENT '请求指纹',
  `response_body` varchar(2048) DEFAULT NULL COMMENT '响应摘要',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_check_in_record_activity` (`activity_id`),
  KEY `idx_check_in_record_voucher` (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动签到核销记录表';

INSERT INTO `tb_activity_check_in_record` VALUES
(583628460014960641,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776852990053-ztqqjefb','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:16:30','2026-04-22 10:16:30'),
(583628481489797122,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776852995052-iqbaylfd','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:16:35','2026-04-22 10:16:35'),
(583628481489797123,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776852995737-yljejtn1','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:16:35','2026-04-22 10:16:35'),
(583628644698554372,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853033321-r561ajp6','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:13','2026-04-22 10:17:13'),
(583628648993521669,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853034410-26qdj2rg','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:14','2026-04-22 10:17:14'),
(583628713418031110,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853049404-h614xob1','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:29','2026-04-22 10:17:29'),
(583628717712998407,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853050316-fjusksmy','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:30','2026-04-22 10:17:30'),
(583628717712998408,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853050694-5alifukr','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:30','2026-04-22 10:17:30'),
(583628717712998409,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853050880-odlizmx6','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:30','2026-04-22 10:17:30'),
(583628722007965706,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853051040-5g0j213l','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:31','2026-04-22 10:17:31'),
(583628722007965707,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853051192-mygba56q','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:31','2026-04-22 10:17:31'),
(583628795022409740,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853068550-imkksm0e','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:17:48','2026-04-22 10:17:48'),
(583628885216722957,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853089787-e4h0suqj','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:18:09','2026-04-22 10:18:09'),
(583628906691559438,3,583628425655222273,1012,1010,'OUT_OF_WINDOW','checkin-1776853094711-kyth108q','eee1a0c8b26e5414040fec5bffb5f7dc','{\"errorMsg\":\"未到签到时间窗口\",\"success\":false}','2026-04-22 10:18:14','2026-04-22 10:18:14'),
(583629142914760719,3,583628425655222273,1012,1010,'SUCCESS','checkin-1776853149053-73mv7z98','eee1a0c8b26e5414040fec5bffb5f7dc','{\"data\":{\"checkedInTime\":1776853149000,\"displayCode\":\"4EPC4S4J\",\"message\":\"签到成功\",\"userId\":1012,\"resultStatus\":\"SUCCESS\",\"checkedInBy\":1010,\"activityId\":3,\"voucherStatus\":\"CHECKED_IN\",\"voucherId\":583628425655222273},\"success\":true}','2026-04-22 10:19:09','2026-04-22 10:19:09'),
(583629413497700368,3,583628425655222273,1012,1010,'ALREADY_CHECKED_IN','checkin-1776853212210-8kesqg7g','eee1a0c8b26e5414040fec5bffb5f7dc','{\"data\":{\"checkedInTime\":1776853149000,\"displayCode\":\"4EPC4S4J\",\"message\":\"该凭证已签到\",\"userId\":1012,\"resultStatus\":\"ALREADY_CHECKED_IN\",\"checkedInBy\":1010,\"activityId\":3,\"voucherStatus\":\"CHECKED_IN\",\"voucherId\":583628425655222273,\"userNickName\":\"user_rs17ymg11f\"},\"success\":true}','2026-04-22 10:20:12','2026-04-22 10:20:12'),
(583629430677569553,3,583628425655222273,1012,1010,'ALREADY_CHECKED_IN','checkin-1776853216333-m5phy498','eee1a0c8b26e5414040fec5bffb5f7dc','{\"data\":{\"checkedInTime\":1776853149000,\"displayCode\":\"4EPC4S4J\",\"message\":\"该凭证已签到\",\"userId\":1012,\"resultStatus\":\"ALREADY_CHECKED_IN\",\"checkedInBy\":1010,\"activityId\":3,\"voucherStatus\":\"CHECKED_IN\",\"voucherId\":583628425655222273,\"userNickName\":\"user_rs17ymg11f\"},\"success\":true}','2026-04-22 10:20:16','2026-04-22 10:20:16');

DROP TABLE IF EXISTS `tb_sign`;
CREATE TABLE `tb_sign` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `year` year NOT NULL,
  `month` tinyint NOT NULL,
  `date` date NOT NULL,
  `is_backup` tinyint unsigned DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) NOT NULL COMMENT '手机号码',
  `password` varchar(128) DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) DEFAULT '' COMMENT '昵称，默认是用户id',
  `icon` varchar(255) DEFAULT '' COMMENT '人物头像',
  `role_type` tinyint(1) NOT NULL DEFAULT '2' COMMENT '账号类型：1主办方，2学生',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniqe_key_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=1013 DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

INSERT INTO `tb_user` VALUES
(1010,'18268159587','','吴一杰','https://xud-campus.oss-cn-beijing.aliyuncs.com/avatars/1010/20260421/96dfcd2900c94d9c83f2c75fec98b35e.jpg',1,'2026-04-21 06:14:05','2026-04-21 12:00:10'),
(1011,'13566984485','','韦佳雯','https://xud-campus.oss-cn-beijing.aliyuncs.com/avatars/1011/20260421/32a466334c7442488de7669d60f26170.jpg',1,'2026-04-21 11:42:46','2026-04-21 11:45:37'),
(1012,'13515789391','','杨世超','',2,'2026-04-22 10:15:14','2026-04-22 10:26:05');

DROP TABLE IF EXISTS `tb_user_info`;
CREATE TABLE `tb_user_info` (
  `user_id` bigint unsigned NOT NULL COMMENT '主键，用户id',
  `city` varchar(64) DEFAULT '' COMMENT '城市名称',
  `college` varchar(64) DEFAULT '' COMMENT '所属学院',
  `grade` varchar(32) DEFAULT '' COMMENT '所属年级',
  `mentor` varchar(64) DEFAULT '' COMMENT '导师',
  `introduce` varchar(128) DEFAULT NULL COMMENT '个人介绍，不要超过128个字符',
  `fans` int unsigned DEFAULT '0' COMMENT '粉丝数量',
  `followee` int unsigned DEFAULT '0' COMMENT '关注的人的数量',
  `gender` tinyint unsigned DEFAULT '0' COMMENT '性别，0：男，1：女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `credits` int unsigned DEFAULT '0' COMMENT '积分',
  `level` tinyint unsigned DEFAULT '0' COMMENT '会员级别，0~9级,0代表未开通会员',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户详情表';

INSERT INTO `tb_user_info` VALUES
(1010,'西安','杭州研究院','2025级','黄晏瑜','',0,0,0,'2003-01-23',0,0,'2026-04-21 08:48:12','2026-04-21 08:48:12'),
(1011,'','杭州研究院','2025','','',0,0,0,NULL,0,0,'2026-04-21 11:43:13','2026-04-21 11:43:13'),
(1012,'杭州','计算机学院','2023','杨鹏飞','',0,0,0,'2003-01-23',0,0,'2026-04-22 10:26:05','2026-04-22 10:26:05');

SET FOREIGN_KEY_CHECKS = 1;
