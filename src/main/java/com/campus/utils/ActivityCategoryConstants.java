package com.campus.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActivityCategoryConstants {

    public static final String PREFERENCE_SOURCE_MANUAL = "MANUAL";

    private static final Map<String, List<String>> CATEGORY_TAGS;
    private static final Map<String, String> DEFAULT_TAGS;

    static {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        tags.put("学术讲座", Arrays.asList("名师讲座", "学术报告", "科研交流", "专业分享", "论文写作", "保研经验", "考研经验", "学科前沿", "实验室开放", "导师面对面"));
        tags.put("就业指导", Arrays.asList("实习", "秋招", "春招", "简历指导", "面试经验", "职业规划", "企业宣讲", "就业分享", "求职技巧", "职场能力", "Java后端", "前端开发", "人工智能", "产品运营"));
        tags.put("竞赛训练", Arrays.asList("学科竞赛", "编程竞赛", "数学建模", "蓝桥杯", "ACM训练", "挑战杯", "互联网+", "电子设计", "机器人竞赛", "英语竞赛", "创新创业竞赛", "赛前培训", "组队招募"));
        tags.put("创新实践", Arrays.asList("创新项目", "创业实践", "科研训练", "项目路演", "实践课程", "技术分享", "创客活动", "实验实践", "产品设计", "项目招募", "创新工坊", "校企合作"));
        tags.put("文艺活动", Arrays.asList("校园歌手", "晚会演出", "舞蹈表演", "摄影展", "读书会", "电影放映", "音乐活动", "戏剧表演", "主持朗诵", "书画展览", "传统文化", "社交舞会"));
        tags.put("体育活动", Arrays.asList("篮球", "足球", "羽毛球", "乒乓球", "排球", "跑步打卡", "运动会", "健身训练", "趣味运动", "户外活动", "体育竞赛", "健康打卡"));
        tags.put("志愿公益", Arrays.asList("志愿服务", "公益实践", "社区服务", "支教活动", "环保行动", "校园服务", "爱心捐助", "文明引导", "公益宣传", "社会实践", "应急志愿", "志愿者招募"));
        tags.put("社团活动", Arrays.asList("社团招新", "社团例会", "社团开放日", "兴趣小组", "社团展示", "社团培训", "社团联谊", "校园组织", "学生会活动", "协会活动", "俱乐部活动", "新生见面会"));
        CATEGORY_TAGS = Collections.unmodifiableMap(tags);

        Map<String, String> defaults = new LinkedHashMap<>();
        tags.forEach((key, value) -> defaults.put(key, value.get(0)));
        DEFAULT_TAGS = Collections.unmodifiableMap(defaults);
    }

    private ActivityCategoryConstants() {
    }

    public static List<String> categoryNames() {
        return CATEGORY_TAGS.keySet().stream().toList();
    }

    public static boolean isValidCategory(String category) {
        return CATEGORY_TAGS.containsKey(category);
    }

    public static Map<String, List<String>> categoryTags() {
        return CATEGORY_TAGS;
    }

    public static String defaultTag(String category) {
        return DEFAULT_TAGS.get(category);
    }
}
