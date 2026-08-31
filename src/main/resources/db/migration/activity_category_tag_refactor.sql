SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `tb_activity_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动一级分类表';

CREATE TABLE IF NOT EXISTS `tb_activity_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `category_id` bigint unsigned NOT NULL,
  `name` varchar(64) NOT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_tag_category_name` (`category_id`,`name`),
  KEY `idx_activity_tag_category` (`category_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动二级标签表';

CREATE TABLE IF NOT EXISTS `tb_activity_tag_relation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `activity_id` bigint unsigned NOT NULL,
  `tag_id` bigint unsigned NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_tag_relation` (`activity_id`,`tag_id`),
  KEY `idx_activity_tag_relation_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动标签关联表';

CREATE TABLE IF NOT EXISTS `tb_user_preference_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `tag_id` bigint unsigned NOT NULL,
  `source` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_preference_tag` (`user_id`,`tag_id`,`source`),
  KEY `idx_user_preference_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动偏好标签表';

INSERT INTO `tb_activity_category` (`name`, `sort_no`, `status`) VALUES
('学术讲座', 1, 1),
('就业指导', 2, 1),
('竞赛训练', 3, 1),
('创新实践', 4, 1),
('文艺活动', 5, 1),
('体育活动', 6, 1),
('志愿公益', 7, 1),
('社团活动', 8, 1)
ON DUPLICATE KEY UPDATE `sort_no` = VALUES(`sort_no`), `status` = VALUES(`status`);

DELETE upt
FROM `tb_user_preference_tag` upt
JOIN `tb_activity_tag` t ON t.`id` = upt.`tag_id`
JOIN `tb_activity_category` c ON c.`id` = t.`category_id`
JOIN (
  SELECT '学术讲座' AS category_name, '前沿分享' AS tag_name UNION ALL
  SELECT '学术讲座', '名师讲堂' UNION ALL
  SELECT '学术讲座', '学科论坛' UNION ALL
  SELECT '学术讲座', '读书交流' UNION ALL
  SELECT '学术讲座', '学术沙龙' UNION ALL
  SELECT '就业指导', '求职训练' UNION ALL
  SELECT '就业指导', '简历面试' UNION ALL
  SELECT '就业指导', '实习辅导' UNION ALL
  SELECT '竞赛训练', '辩论训练' UNION ALL
  SELECT '竞赛训练', '赛前集训' UNION ALL
  SELECT '创新实践', '创客实践' UNION ALL
  SELECT '创新实践', '实验开放' UNION ALL
  SELECT '创新实践', '产学协作' UNION ALL
  SELECT '文艺活动', '音乐演出' UNION ALL
  SELECT '文艺活动', '戏剧展演' UNION ALL
  SELECT '文艺活动', '影像放映' UNION ALL
  SELECT '文艺活动', '美育工作坊' UNION ALL
  SELECT '文艺活动', '节庆晚会' UNION ALL
  SELECT '体育活动', '球类赛事' UNION ALL
  SELECT '体育活动', '跑步健身' UNION ALL
  SELECT '体育活动', '户外拓展' UNION ALL
  SELECT '体育活动', '体育训练' UNION ALL
  SELECT '志愿公益', '助学帮扶' UNION ALL
  SELECT '志愿公益', '大型赛会志愿' UNION ALL
  SELECT '社团活动', '骨干培训' UNION ALL
  SELECT '社团活动', '主题交流' UNION ALL
  SELECT '社团活动', '联谊活动' UNION ALL
  SELECT '社团活动', '例会沙龙'
) legacy ON legacy.category_name = c.`name` AND legacy.tag_name = t.`name`;

DELETE r
FROM `tb_activity_tag_relation` r
JOIN `tb_activity_tag` t ON t.`id` = r.`tag_id`
JOIN `tb_activity_category` c ON c.`id` = t.`category_id`
JOIN (
  SELECT '学术讲座' AS category_name, '前沿分享' AS tag_name UNION ALL
  SELECT '学术讲座', '名师讲堂' UNION ALL
  SELECT '学术讲座', '学科论坛' UNION ALL
  SELECT '学术讲座', '读书交流' UNION ALL
  SELECT '学术讲座', '学术沙龙' UNION ALL
  SELECT '就业指导', '求职训练' UNION ALL
  SELECT '就业指导', '简历面试' UNION ALL
  SELECT '就业指导', '实习辅导' UNION ALL
  SELECT '竞赛训练', '辩论训练' UNION ALL
  SELECT '竞赛训练', '赛前集训' UNION ALL
  SELECT '创新实践', '创客实践' UNION ALL
  SELECT '创新实践', '实验开放' UNION ALL
  SELECT '创新实践', '产学协作' UNION ALL
  SELECT '文艺活动', '音乐演出' UNION ALL
  SELECT '文艺活动', '戏剧展演' UNION ALL
  SELECT '文艺活动', '影像放映' UNION ALL
  SELECT '文艺活动', '美育工作坊' UNION ALL
  SELECT '文艺活动', '节庆晚会' UNION ALL
  SELECT '体育活动', '球类赛事' UNION ALL
  SELECT '体育活动', '跑步健身' UNION ALL
  SELECT '体育活动', '户外拓展' UNION ALL
  SELECT '体育活动', '体育训练' UNION ALL
  SELECT '志愿公益', '助学帮扶' UNION ALL
  SELECT '志愿公益', '大型赛会志愿' UNION ALL
  SELECT '社团活动', '骨干培训' UNION ALL
  SELECT '社团活动', '主题交流' UNION ALL
  SELECT '社团活动', '联谊活动' UNION ALL
  SELECT '社团活动', '例会沙龙'
) legacy ON legacy.category_name = c.`name` AND legacy.tag_name = t.`name`;

DELETE t
FROM `tb_activity_tag` t
JOIN `tb_activity_category` c ON c.`id` = t.`category_id`
JOIN (
  SELECT '学术讲座' AS category_name, '前沿分享' AS tag_name UNION ALL
  SELECT '学术讲座', '名师讲堂' UNION ALL
  SELECT '学术讲座', '学科论坛' UNION ALL
  SELECT '学术讲座', '读书交流' UNION ALL
  SELECT '学术讲座', '学术沙龙' UNION ALL
  SELECT '就业指导', '求职训练' UNION ALL
  SELECT '就业指导', '简历面试' UNION ALL
  SELECT '就业指导', '实习辅导' UNION ALL
  SELECT '竞赛训练', '辩论训练' UNION ALL
  SELECT '竞赛训练', '赛前集训' UNION ALL
  SELECT '创新实践', '创客实践' UNION ALL
  SELECT '创新实践', '实验开放' UNION ALL
  SELECT '创新实践', '产学协作' UNION ALL
  SELECT '文艺活动', '音乐演出' UNION ALL
  SELECT '文艺活动', '戏剧展演' UNION ALL
  SELECT '文艺活动', '影像放映' UNION ALL
  SELECT '文艺活动', '美育工作坊' UNION ALL
  SELECT '文艺活动', '节庆晚会' UNION ALL
  SELECT '体育活动', '球类赛事' UNION ALL
  SELECT '体育活动', '跑步健身' UNION ALL
  SELECT '体育活动', '户外拓展' UNION ALL
  SELECT '体育活动', '体育训练' UNION ALL
  SELECT '志愿公益', '助学帮扶' UNION ALL
  SELECT '志愿公益', '大型赛会志愿' UNION ALL
  SELECT '社团活动', '骨干培训' UNION ALL
  SELECT '社团活动', '主题交流' UNION ALL
  SELECT '社团活动', '联谊活动' UNION ALL
  SELECT '社团活动', '例会沙龙'
) legacy ON legacy.category_name = c.`name` AND legacy.tag_name = t.`name`;

INSERT INTO `tb_activity_tag` (`category_id`, `name`, `sort_no`, `status`)
SELECT c.`id`, t.`name`, t.`sort_no`, 1
FROM `tb_activity_category` c
JOIN (
  SELECT '学术讲座' AS category_name, '名师讲座' AS name, 1 AS sort_no UNION ALL
  SELECT '学术讲座', '学术报告', 2 UNION ALL
  SELECT '学术讲座', '科研交流', 3 UNION ALL
  SELECT '学术讲座', '专业分享', 4 UNION ALL
  SELECT '学术讲座', '论文写作', 5 UNION ALL
  SELECT '学术讲座', '保研经验', 6 UNION ALL
  SELECT '学术讲座', '考研经验', 7 UNION ALL
  SELECT '学术讲座', '学科前沿', 8 UNION ALL
  SELECT '学术讲座', '实验室开放', 9 UNION ALL
  SELECT '学术讲座', '导师面对面', 10 UNION ALL
  SELECT '就业指导', '实习', 1 UNION ALL
  SELECT '就业指导', '秋招', 2 UNION ALL
  SELECT '就业指导', '春招', 3 UNION ALL
  SELECT '就业指导', '简历指导', 4 UNION ALL
  SELECT '就业指导', '面试经验', 5 UNION ALL
  SELECT '就业指导', '职业规划', 6 UNION ALL
  SELECT '就业指导', '企业宣讲', 7 UNION ALL
  SELECT '就业指导', '就业分享', 8 UNION ALL
  SELECT '就业指导', '求职技巧', 9 UNION ALL
  SELECT '就业指导', '职场能力', 10 UNION ALL
  SELECT '就业指导', 'Java后端', 11 UNION ALL
  SELECT '就业指导', '前端开发', 12 UNION ALL
  SELECT '就业指导', '人工智能', 13 UNION ALL
  SELECT '就业指导', '产品运营', 14 UNION ALL
  SELECT '竞赛训练', '学科竞赛', 1 UNION ALL
  SELECT '竞赛训练', '编程竞赛', 2 UNION ALL
  SELECT '竞赛训练', '数学建模', 3 UNION ALL
  SELECT '竞赛训练', '蓝桥杯', 4 UNION ALL
  SELECT '竞赛训练', 'ACM训练', 5 UNION ALL
  SELECT '竞赛训练', '挑战杯', 6 UNION ALL
  SELECT '竞赛训练', '互联网+', 7 UNION ALL
  SELECT '竞赛训练', '电子设计', 8 UNION ALL
  SELECT '竞赛训练', '机器人竞赛', 9 UNION ALL
  SELECT '竞赛训练', '英语竞赛', 10 UNION ALL
  SELECT '竞赛训练', '创新创业竞赛', 11 UNION ALL
  SELECT '竞赛训练', '赛前培训', 12 UNION ALL
  SELECT '竞赛训练', '组队招募', 13 UNION ALL
  SELECT '创新实践', '创新项目', 1 UNION ALL
  SELECT '创新实践', '创业实践', 2 UNION ALL
  SELECT '创新实践', '科研训练', 3 UNION ALL
  SELECT '创新实践', '项目路演', 4 UNION ALL
  SELECT '创新实践', '实践课程', 5 UNION ALL
  SELECT '创新实践', '技术分享', 6 UNION ALL
  SELECT '创新实践', '创客活动', 7 UNION ALL
  SELECT '创新实践', '实验实践', 8 UNION ALL
  SELECT '创新实践', '产品设计', 9 UNION ALL
  SELECT '创新实践', '项目招募', 10 UNION ALL
  SELECT '创新实践', '创新工坊', 11 UNION ALL
  SELECT '创新实践', '校企合作', 12 UNION ALL
  SELECT '文艺活动', '校园歌手', 1 UNION ALL
  SELECT '文艺活动', '晚会演出', 2 UNION ALL
  SELECT '文艺活动', '舞蹈表演', 3 UNION ALL
  SELECT '文艺活动', '摄影展', 4 UNION ALL
  SELECT '文艺活动', '读书会', 5 UNION ALL
  SELECT '文艺活动', '电影放映', 6 UNION ALL
  SELECT '文艺活动', '音乐活动', 7 UNION ALL
  SELECT '文艺活动', '戏剧表演', 8 UNION ALL
  SELECT '文艺活动', '主持朗诵', 9 UNION ALL
  SELECT '文艺活动', '书画展览', 10 UNION ALL
  SELECT '文艺活动', '传统文化', 11 UNION ALL
  SELECT '文艺活动', '社交舞会', 12 UNION ALL
  SELECT '体育活动', '篮球', 1 UNION ALL
  SELECT '体育活动', '足球', 2 UNION ALL
  SELECT '体育活动', '羽毛球', 3 UNION ALL
  SELECT '体育活动', '乒乓球', 4 UNION ALL
  SELECT '体育活动', '排球', 5 UNION ALL
  SELECT '体育活动', '跑步打卡', 6 UNION ALL
  SELECT '体育活动', '运动会', 7 UNION ALL
  SELECT '体育活动', '健身训练', 8 UNION ALL
  SELECT '体育活动', '趣味运动', 9 UNION ALL
  SELECT '体育活动', '户外活动', 10 UNION ALL
  SELECT '体育活动', '体育竞赛', 11 UNION ALL
  SELECT '体育活动', '健康打卡', 12 UNION ALL
  SELECT '志愿公益', '志愿服务', 1 UNION ALL
  SELECT '志愿公益', '公益实践', 2 UNION ALL
  SELECT '志愿公益', '社区服务', 3 UNION ALL
  SELECT '志愿公益', '支教活动', 4 UNION ALL
  SELECT '志愿公益', '环保行动', 5 UNION ALL
  SELECT '志愿公益', '校园服务', 6 UNION ALL
  SELECT '志愿公益', '爱心捐助', 7 UNION ALL
  SELECT '志愿公益', '文明引导', 8 UNION ALL
  SELECT '志愿公益', '公益宣传', 9 UNION ALL
  SELECT '志愿公益', '社会实践', 10 UNION ALL
  SELECT '志愿公益', '应急志愿', 11 UNION ALL
  SELECT '志愿公益', '志愿者招募', 12 UNION ALL
  SELECT '社团活动', '社团招新', 1 UNION ALL
  SELECT '社团活动', '社团例会', 2 UNION ALL
  SELECT '社团活动', '社团开放日', 3 UNION ALL
  SELECT '社团活动', '兴趣小组', 4 UNION ALL
  SELECT '社团活动', '社团展示', 5 UNION ALL
  SELECT '社团活动', '社团培训', 6 UNION ALL
  SELECT '社团活动', '社团联谊', 7 UNION ALL
  SELECT '社团活动', '校园组织', 8 UNION ALL
  SELECT '社团活动', '学生会活动', 9 UNION ALL
  SELECT '社团活动', '协会活动', 10 UNION ALL
  SELECT '社团活动', '俱乐部活动', 11 UNION ALL
  SELECT '社团活动', '新生见面会', 12
) t ON t.category_name = c.`name`
ON DUPLICATE KEY UPDATE `sort_no` = VALUES(`sort_no`), `status` = VALUES(`status`);

UPDATE `tb_activity`
SET `category` = CASE
  WHEN `category` IN ('公益活动', '志愿服务') THEN '志愿公益'
  WHEN `category` = '其他' THEN '社团活动'
  ELSE `category`
END
WHERE `category` IN ('公益活动', '志愿服务', '其他');

INSERT INTO `tb_activity_tag` (`category_id`, `name`, `sort_no`, `status`)
SELECT c.`id`, x.`custom_category`, 99, 1
FROM (
  SELECT DISTINCT TRIM(`custom_category`) AS `custom_category`
  FROM `tb_activity`
  WHERE TRIM(IFNULL(`custom_category`, '')) <> ''
) x
JOIN `tb_activity_category` c ON c.`name` = '社团活动'
LEFT JOIN `tb_activity_tag` t ON t.`category_id` = c.`id` AND t.`name` = x.`custom_category`
WHERE t.`id` IS NULL;

INSERT IGNORE INTO `tb_activity_tag_relation` (`activity_id`, `tag_id`)
SELECT a.`id`, t.`id`
FROM `tb_activity` a
JOIN `tb_activity_category` c ON c.`name` = a.`category`
JOIN `tb_activity_tag` t ON t.`category_id` = c.`id`
WHERE TRIM(IFNULL(a.`custom_category`, '')) <> ''
  AND (
    t.`name` = TRIM(a.`custom_category`)
    OR (a.`category` = '创新实践' AND t.`name` = '创新项目')
    OR (a.`category` = '竞赛训练' AND t.`name` = '赛前培训')
    OR (a.`category` = '文艺活动' AND t.`name` = '晚会演出')
    OR (a.`category` = '志愿公益' AND t.`name` = '志愿服务')
    OR (a.`category` = '社团活动' AND t.`name` = '社团展示')
  );

INSERT IGNORE INTO `tb_activity_tag_relation` (`activity_id`, `tag_id`)
SELECT a.`id`, t.`id`
FROM `tb_activity` a
JOIN `tb_activity_category` c ON c.`name` = a.`category`
JOIN `tb_activity_tag` t ON t.`category_id` = c.`id`
LEFT JOIN `tb_activity_tag_relation` r ON r.`activity_id` = a.`id`
WHERE r.`id` IS NULL
  AND (
    (a.`category` = '学术讲座' AND t.`name` = '名师讲座')
    OR (a.`category` = '就业指导' AND t.`name` = '职业规划')
    OR (a.`category` = '竞赛训练' AND t.`name` = '赛前培训')
    OR (a.`category` = '创新实践' AND t.`name` = '创新项目')
    OR (a.`category` = '文艺活动' AND t.`name` = '晚会演出')
    OR (a.`category` = '体育活动' AND t.`name` = '体育竞赛')
    OR (a.`category` = '志愿公益' AND t.`name` = '志愿服务')
    OR (a.`category` = '社团活动' AND t.`name` = '社团展示')
  );

SET FOREIGN_KEY_CHECKS = 1;
