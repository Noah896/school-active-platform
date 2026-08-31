package com.campus.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.config.ActivityAiReviewProperties;
import com.campus.dto.ActivityAiReviewEventDTO;
import com.campus.dto.ActivityAiReviewReportDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityAiReviewRecord;
import com.campus.mapper.ActivityAiReviewRecordMapper;
import com.campus.mapper.ActivityMapper;
import com.campus.service.IActivityAiReviewService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ActivityAiReviewServiceImpl implements IActivityAiReviewService {

    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_RUNNING = "RUNNING";
    private static final String TASK_SUCCESS = "SUCCESS";
    private static final String TASK_FAILED = "FAILED";
    private static final String TASK_TIMEOUT = "TIMEOUT";
    private static final String SUGGESTION_PASS = "PASS";
    private static final String SUGGESTION_MANUAL_REVIEW = "MANUAL_REVIEW";
    private static final String SUGGESTION_REJECT = "REJECT";
    private static final String SUGGESTION_UNKNOWN = "UNKNOWN";
    private static final String RISK_UNKNOWN = "UNKNOWN";
    private static final String PARSE_PARSED = "PARSED";
    private static final String PARSE_FAILED = "PARSE_FAILED";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int STATUS_PUBLISHED = 2;
    private static final int STATUS_OFFLINE_PENDING_REVIEW = 5;

    @Resource
    private ActivityAiReviewRecordMapper activityAiReviewRecordMapper;

    @Resource
    private ActivityMapper activityMapper;

    @Resource
    private ActivityAiReviewProperties properties;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private Environment environment;

    private final RestHighLevelClient restHighLevelClient;
    private final ChatClient chatClient;

    public ActivityAiReviewServiceImpl(ObjectProvider<RestHighLevelClient> clientProvider,
                                       ObjectProvider<ChatClient> chatClientProvider) {
        this.restHighLevelClient = clientProvider.getIfAvailable();
        this.chatClient = chatClientProvider.getIfAvailable();
    }

    @Override
    public void scheduleActivityReview(Activity activity, String trigger) {
        if (activity == null || activity.getId() == null) {
            return;
        }
        String promptVersion = currentPromptVersion();
        if (!isAiRuntimeAvailable()) {
            upsertUnavailableRecord(activity.getId(), promptVersion, "AI 审核未启用或未配置 QWEN_API_KEY");
            return;
        }
        upsertPendingRecord(activity.getId(), promptVersion);
        ActivityAiReviewEventDTO event = new ActivityAiReviewEventDTO();
        event.setActivityId(activity.getId());
        event.setPromptVersion(promptVersion);
        event.setTrigger(StrUtil.blankToDefault(trigger, "UPSERT"));
        event.setEventTime(LocalDateTime.now());
        dispatch(event);
    }

    @Override
    public void consume(ActivityAiReviewEventDTO event) {
        if (event == null || event.getActivityId() == null) {
            return;
        }
        String promptVersion = StrUtil.blankToDefault(event.getPromptVersion(), currentPromptVersion());
        ActivityAiReviewRecord record = findRecord(event.getActivityId(), promptVersion);
        if (record == null) {
            upsertPendingRecord(event.getActivityId(), promptVersion);
            record = findRecord(event.getActivityId(), promptVersion);
        }
        if (record == null) {
            return;
        }
        if (Objects.equals(record.getTaskStatus(), TASK_SUCCESS)
                && StrUtil.isNotBlank(record.getReviewComment())
                && StrUtil.equals(record.getPromptVersion(), promptVersion)) {
            return;
        }
        Activity activity = activityMapper.selectById(event.getActivityId());
        if (activity == null) {
            markFailure(record, TASK_FAILED, "活动不存在");
            return;
        }
        if (!isAiRuntimeAvailable()) {
            markFailure(record, TASK_FAILED, "AI 审核未启用或未配置 QWEN_API_KEY");
            return;
        }
        markRunning(record.getId());
        try {
            RuleCheckResult ruleCheckResult = runRuleCheck(activity);
            SimilarSearchResult similarResult = searchSimilarActivities(activity);
            String prompt = buildPrompt(activity, ruleCheckResult, similarResult);
            String rawResponse = callModel(prompt);
            log.info("活动 AI 模型调用成功 activityId={}, promptVersion={}, model={}",
                    activity.getId(), promptVersion,
                    environment.getProperty("spring.ai.openai.chat.options.model", "qwen-plus"));
            persistSuccess(record.getId(), activity.getId(), promptVersion, rawResponse, similarResult);
        } catch (Exception e) {
            String message = e.getMessage();
            String status = isTimeoutMessage(message) ? TASK_TIMEOUT : TASK_FAILED;
            log.warn("活动 AI 审核失败 activityId={}, promptVersion={}",
                    event.getActivityId(), promptVersion, e);
            markFailure(record, status, StrUtil.blankToDefault(message, "AI 审核调用失败"));
        }
    }

    @Override
    public ActivityAiReviewReportDTO queryReport(Long activityId) {
        ActivityAiReviewReportDTO dto = new ActivityAiReviewReportDTO();
        dto.setActivityId(activityId);
        ActivityAiReviewRecord record = findRecord(activityId, currentPromptVersion());
        if (record == null) {
            dto.setTaskStatus("NOT_FOUND");
            dto.setSuggestion(SUGGESTION_UNKNOWN);
            dto.setRiskLevel(RISK_UNKNOWN);
            dto.setParseStatus(PARSE_PARSED);
            dto.setReviewComment("暂无 AI 审核报告");
            return dto;
        }
        dto.setTaskStatus(record.getTaskStatus());
        dto.setSuggestion(record.getSuggestion());
        dto.setRiskLevel(record.getRiskLevel());
        dto.setScore(record.getScore());
        dto.setProblems(parseStringList(record.getProblemsJson()));
        dto.setMissingFields(parseStringList(record.getMissingFieldsJson()));
        dto.setSimilarActivities(parseObjectList(record.getSimilarActivitiesJson()));
        dto.setSimilarityAnalysis(record.getSimilarityAnalysis());
        dto.setReviewComment(record.getReviewComment());
        dto.setModelName(record.getModelName());
        dto.setPromptVersion(record.getPromptVersion());
        dto.setParseStatus(record.getParseStatus());
        dto.setErrorMessage(record.getErrorMessage());
        return dto;
    }

    @Override
    public void recordManualReview(Activity activity, Boolean approved, String remark) {
        if (activity == null || activity.getId() == null || approved == null) {
            return;
        }
        ActivityAiReviewRecord record = findRecord(activity.getId(), currentPromptVersion());
        if (record == null) {
            return;
        }
        ActivityAiReviewRecord update = new ActivityAiReviewRecord();
        update.setId(record.getId());
        update.setManualReviewResult(Boolean.TRUE.equals(approved) ? "APPROVED" : "REJECTED");
        update.setManualReviewRemark(StrUtil.trim(remark));
        update.setManualReviewedAt(LocalDateTime.now());
        update.setIsAiAdopted(resolveAdopted(record.getSuggestion(), approved));
        activityAiReviewRecordMapper.updateById(update);
    }

    @Scheduled(cron = "${campus.activity.ai-review.compensation-cron:0 */3 * * * ?}")
    public void compensatePendingTasks() {
        if (!isAiRuntimeAvailable()) {
            return;
        }
        List<ActivityAiReviewRecord> records = activityAiReviewRecordMapper.selectList(new QueryWrapper<ActivityAiReviewRecord>()
                .eq("prompt_version", currentPromptVersion())
                .eq("task_status", TASK_PENDING)
                .orderByAsc("updated_at")
                .last("limit 20"));
        if (CollUtil.isEmpty(records)) {
            return;
        }
        for (ActivityAiReviewRecord record : records) {
            ActivityAiReviewEventDTO event = new ActivityAiReviewEventDTO();
            event.setActivityId(record.getActivityId());
            event.setPromptVersion(record.getPromptVersion());
            event.setTrigger("COMPENSATE");
            event.setEventTime(LocalDateTime.now());
            dispatch(event);
        }
    }

    private void dispatch(ActivityAiReviewEventDTO event) {
        Runnable action = () -> sendEvent(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void sendEvent(ActivityAiReviewEventDTO event) {
        try {
            rocketMQTemplate.convertAndSend(properties.getTopic(), event);
        } catch (Exception e) {
            log.warn("发送活动 AI 审核消息失败，改为本地执行 activityId={}", event.getActivityId(), e);
            consume(event);
        }
    }

    private String callModel(String prompt) {
        if (chatClient == null) {
            throw new IllegalStateException("Spring AI ChatClient 不可用");
        }
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(environment.getProperty("spring.ai.openai.chat.options.model", "qwen-plus"))
                .temperature(0.1D)
                .build();
        return chatClient.prompt(new Prompt(prompt, options)).call().content();
    }

    private void persistSuccess(Long recordId,
                                Long activityId,
                                String promptVersion,
                                String rawResponse,
                                SimilarSearchResult similarResult) {
        ActivityAiReviewRecord update = new ActivityAiReviewRecord();
        update.setId(recordId);
        update.setTaskStatus(TASK_SUCCESS);
        update.setRawResponse(rawResponse);
        update.setPromptVersion(promptVersion);
        update.setModelName(environment.getProperty("spring.ai.openai.chat.options.model", "qwen-plus"));
        update.setParseStatus(PARSE_PARSED);
        update.setErrorMessage(null);
        try {
            JSONObject parsed = parseJsonObject(rawResponse);
            String suggestion = normalizeSuggestion(parsed.getStr("suggestion"));
            String riskLevel = normalizeRiskLevel(parsed.getStr("riskLevel"));
            update.setSuggestion(suggestion);
            update.setRiskLevel(riskLevel);
            update.setScore(parsed.getInt("score"));
            update.setProblemsJson(toJsonArrayString(parsed.getJSONArray("problems")));
            update.setMissingFieldsJson(toJsonArrayString(parsed.getJSONArray("missingFields")));
            update.setSimilarActivitiesJson(JSONUtil.parseArray(similarResult.activities).toString());
            update.setSimilarityAnalysis(resolveSimilarityAnalysis(parsed, similarResult));
            update.setReviewComment(resolveReviewComment(parsed, similarResult));
            log.info("活动 AI 审核报告入库成功 activityId={}, promptVersion={}, suggestion={}, riskLevel={}, score={}",
                    activityId, promptVersion, suggestion, riskLevel, update.getScore());
        } catch (Exception e) {
            update.setSuggestion(SUGGESTION_MANUAL_REVIEW);
            update.setRiskLevel(RISK_UNKNOWN);
            update.setParseStatus(PARSE_FAILED);
            update.setErrorMessage("AI 返回格式异常，请管理员人工审核");
            update.setSimilarityAnalysis(similarResult.note);
            update.setReviewComment("AI 返回格式异常，请管理员人工审核");
            log.warn("活动 AI 审核返回已收到但解析失败 activityId={}, promptVersion={}",
                    activityId, promptVersion, e);
        }
        activityAiReviewRecordMapper.updateById(update);
    }

    private JSONObject parseJsonObject(String rawResponse) {
        if (StrUtil.isBlank(rawResponse)) {
            throw new IllegalArgumentException("AI 返回为空");
        }
        String trimmed = rawResponse.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("AI 返回不是 JSON");
        }
        return JSONUtil.parseObj(trimmed.substring(start, end + 1));
    }

    private String resolveSimilarityAnalysis(JSONObject parsed, SimilarSearchResult similarResult) {
        String value = StrUtil.trim(parsed.getStr("similarityAnalysis"));
        if (StrUtil.isNotBlank(value)) {
            return value;
        }
        return similarResult.note;
    }

    private String resolveReviewComment(JSONObject parsed, SimilarSearchResult similarResult) {
        String value = StrUtil.trim(parsed.getStr("reviewComment"));
        if (StrUtil.isNotBlank(value)) {
            return value;
        }
        if (StrUtil.isNotBlank(similarResult.note)) {
            return "建议管理员结合规则校验和相似活动情况进行人工复核。";
        }
        return "AI 已完成初步分析，请管理员结合活动详情进行人工审核。";
    }

    private void markRunning(Long id) {
        ActivityAiReviewRecord update = new ActivityAiReviewRecord();
        update.setId(id);
        update.setTaskStatus(TASK_RUNNING);
        update.setErrorMessage(null);
        activityAiReviewRecordMapper.updateById(update);
    }

    private void markFailure(ActivityAiReviewRecord record, String taskStatus, String message) {
        if (record == null || record.getId() == null) {
            return;
        }
        ActivityAiReviewRecord update = new ActivityAiReviewRecord();
        update.setId(record.getId());
        update.setTaskStatus(taskStatus);
        update.setSuggestion(SUGGESTION_MANUAL_REVIEW);
        update.setRiskLevel(RISK_UNKNOWN);
        update.setParseStatus(PARSE_PARSED);
        update.setErrorMessage(StrUtil.maxLength(StrUtil.blankToDefault(message, "AI 审核失败，请管理员人工审核"), 1000));
        update.setReviewComment("AI 审核暂不可用，请管理员人工审核");
        activityAiReviewRecordMapper.updateById(update);
    }

    private void upsertPendingRecord(Long activityId, String promptVersion) {
        ActivityAiReviewRecord existing = findRecord(activityId, promptVersion);
        if (existing == null) {
            ActivityAiReviewRecord record = new ActivityAiReviewRecord();
            record.setActivityId(activityId);
            record.setTaskStatus(TASK_PENDING);
            record.setSuggestion(SUGGESTION_UNKNOWN);
            record.setRiskLevel(RISK_UNKNOWN);
            record.setPromptVersion(promptVersion);
            record.setParseStatus(PARSE_PARSED);
            record.setReviewComment("AI 审核报告生成中，请稍候");
            activityAiReviewRecordMapper.insert(record);
            return;
        }
        ActivityAiReviewRecord update = new ActivityAiReviewRecord();
        update.setId(existing.getId());
        update.setTaskStatus(TASK_PENDING);
        update.setSuggestion(SUGGESTION_UNKNOWN);
        update.setRiskLevel(RISK_UNKNOWN);
        update.setScore(null);
        update.setProblemsJson(null);
        update.setMissingFieldsJson(null);
        update.setSimilarActivitiesJson(null);
        update.setSimilarityAnalysis(null);
        update.setReviewComment("AI 审核报告生成中，请稍候");
        update.setRawResponse(null);
        update.setParseStatus(PARSE_PARSED);
        update.setErrorMessage(null);
        update.setManualReviewResult(null);
        update.setManualReviewRemark(null);
        update.setManualReviewedAt(null);
        update.setIsAiAdopted(null);
        activityAiReviewRecordMapper.updateById(update);
    }

    private void upsertUnavailableRecord(Long activityId, String promptVersion, String message) {
        ActivityAiReviewRecord existing = findRecord(activityId, promptVersion);
        if (existing == null) {
            ActivityAiReviewRecord record = new ActivityAiReviewRecord();
            record.setActivityId(activityId);
            record.setPromptVersion(promptVersion);
            record.setTaskStatus(TASK_FAILED);
            record.setSuggestion(SUGGESTION_MANUAL_REVIEW);
            record.setRiskLevel(RISK_UNKNOWN);
            record.setParseStatus(PARSE_PARSED);
            record.setErrorMessage(message);
            record.setReviewComment("AI 审核暂不可用，请管理员人工审核");
            activityAiReviewRecordMapper.insert(record);
            return;
        }
        markFailure(existing, TASK_FAILED, message);
    }

    private ActivityAiReviewRecord findRecord(Long activityId, String promptVersion) {
        if (activityId == null || StrUtil.isBlank(promptVersion)) {
            return null;
        }
        return activityAiReviewRecordMapper.selectOne(new QueryWrapper<ActivityAiReviewRecord>()
                .eq("activity_id", activityId)
                .eq("prompt_version", promptVersion)
                .last("limit 1"));
    }

    private String currentPromptVersion() {
        return StrUtil.blankToDefault(properties.getPromptVersion(), "v1");
    }

    private boolean isAiRuntimeAvailable() {
        return properties.isEnabled()
                && StrUtil.isNotBlank(environment.getProperty("spring.ai.openai.api-key"))
                && chatClient != null;
    }

    private boolean isTimeoutMessage(String message) {
        String normalized = StrUtil.nullToEmpty(message).toLowerCase(Locale.ROOT);
        return StrUtil.containsAny(normalized, "timeout", "timed out", "read timed out");
    }

    private Boolean resolveAdopted(String suggestion, Boolean approved) {
        if (approved == null) {
            return null;
        }
        if (SUGGESTION_PASS.equals(suggestion)) {
            return approved;
        }
        if (SUGGESTION_REJECT.equals(suggestion)) {
            return !approved;
        }
        return false;
    }

    private String normalizeSuggestion(String value) {
        if (SUGGESTION_PASS.equalsIgnoreCase(value)) {
            return SUGGESTION_PASS;
        }
        if (SUGGESTION_REJECT.equalsIgnoreCase(value)) {
            return SUGGESTION_REJECT;
        }
        if (SUGGESTION_MANUAL_REVIEW.equalsIgnoreCase(value)) {
            return SUGGESTION_MANUAL_REVIEW;
        }
        return SUGGESTION_MANUAL_REVIEW;
    }

    private String normalizeRiskLevel(String value) {
        if (StrUtil.isBlank(value)) {
            return RISK_UNKNOWN;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (Objects.equals(normalized, "LOW")
                || Objects.equals(normalized, "MEDIUM")
                || Objects.equals(normalized, "HIGH")) {
            return normalized;
        }
        return RISK_UNKNOWN;
    }

    private String toJsonArrayString(JSONArray array) {
        return array == null ? JSONUtil.parseArray(Collections.emptyList()).toString() : array.toString();
    }

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        JSONArray array = JSONUtil.parseArray(json);
        List<String> result = new ArrayList<>(array.size());
        for (Object item : array) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseObjectList(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        JSONArray array = JSONUtil.parseArray(json);
        List<Map<String, Object>> result = new ArrayList<>(array.size());
        for (Object item : array) {
            if (item instanceof JSONObject jsonObject) {
                result.add(new LinkedHashMap<>(jsonObject));
            }
        }
        return result;
    }

    private RuleCheckResult runRuleCheck(Activity activity) {
        RuleCheckResult result = new RuleCheckResult();
        if (activity == null) {
            result.problems.add("活动不存在");
            return result;
        }
        if (StrUtil.isBlank(activity.getSummary()) || StrUtil.length(StrUtil.trim(activity.getSummary())) < 12) {
            result.problems.add("活动简介较短，建议补充活动背景和目标");
        }
        if (StrUtil.isBlank(activity.getContent()) || StrUtil.length(StrUtil.trim(activity.getContent())) < 40) {
            result.problems.add("活动详情描述偏短，建议补充流程、参与方式和注意事项");
        }
        if (StrUtil.isBlank(activity.getOrganizerName())) {
            result.missingFields.add("主办方名称");
        }
        if (StrUtil.isBlank(activity.getContactInfo())) {
            result.missingFields.add("主办方联系方式");
        }
        if (StrUtil.isBlank(activity.getLocation())) {
            result.missingFields.add("活动地点");
        }
        if (activity.getMaxParticipants() == null || activity.getMaxParticipants() <= 0) {
            result.problems.add("人数上限设置异常");
        }
        if (activity.getRegistrationStartTime() == null || activity.getRegistrationEndTime() == null) {
            result.missingFields.add("报名时间");
        }
        if (activity.getEventStartTime() == null || activity.getEventEndTime() == null) {
            result.missingFields.add("活动时间");
        }
        if (activity.getRegistrationEndTime() != null && activity.getEventStartTime() != null
                && activity.getRegistrationEndTime().isAfter(activity.getEventStartTime())) {
            result.problems.add("报名截止时间晚于活动开始时间");
        }
        if (activity.getEventStartTime() != null && activity.getEventEndTime() != null
                && !activity.getEventStartTime().isBefore(activity.getEventEndTime())) {
            result.problems.add("活动时间区间设置异常");
        }
        return result;
    }

    private SimilarSearchResult searchSimilarActivities(Activity activity) {
        SimilarSearchResult result = new SimilarSearchResult();
        if (activity == null || activity.getId() == null || restHighLevelClient == null) {
            result.note = "历史相似活动召回失败，本次未进行相似活动分析。";
            return result;
        }
        String indexName = environment.getProperty("campus.activity.search.es.index-name", "activity_index_dev");
        try {
            SearchRequest request = new SearchRequest(indexName);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder query = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.termsQuery("status", List.of(STATUS_PUBLISHED, STATUS_OFFLINE_PENDING_REVIEW)))
                    .mustNot(QueryBuilders.termQuery("_id", String.valueOf(activity.getId())));
            String keyword = StrUtil.blankToDefault(activity.getTitle(), StrUtil.blankToDefault(activity.getSummary(), activity.getCategory()));
            if (StrUtil.isNotBlank(keyword)) {
                query.must(QueryBuilders.multiMatchQuery(keyword)
                        .field("title", 4.0f)
                        .field("category", 3.0f)
                        .field("tags", 3.0f)
                        .field("summary", 2.0f)
                        .field("content", 1.0f)
                        .field("organizerName", 1.0f)
                        .type(MultiMatchQueryBuilder.Type.BEST_FIELDS));
            }
            sourceBuilder.query(query);
            sourceBuilder.size(3);
            sourceBuilder.fetchSource(false);
            request.source(sourceBuilder);
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            List<Long> ids = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                ids.add(Long.valueOf(hit.getId()));
            }
            if (ids.isEmpty()) {
                result.note = "未检索到明显相似的历史活动。";
                return result;
            }
            List<Activity> activities = activityMapper.selectBatchIds(ids);
            Map<Long, Activity> activityMap = activities.stream()
                    .collect(Collectors.toMap(Activity::getId, item -> item, (a, b) -> a, HashMap::new));
            for (Long id : ids) {
                Activity item = activityMap.get(id);
                if (item == null) {
                    continue;
                }
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", item.getId());
                summary.put("title", item.getTitle());
                summary.put("category", item.getCategory());
                summary.put("displayCategory", item.getCategory());
                summary.put("organizerName", item.getOrganizerName());
                summary.put("location", item.getLocation());
                summary.put("eventStartTime", formatTime(item.getEventStartTime()));
                summary.put("coverImage", item.getCoverImage());
                result.activities.add(summary);
            }
            result.note = "已召回 " + result.activities.size() + " 条相似活动供 AI 参考。";
        } catch (Exception e) {
            log.warn("相似活动召回失败 activityId={}", activity.getId(), e);
            result.note = "历史相似活动召回失败，本次未进行相似活动分析。";
        }
        return result;
    }

    private String buildPrompt(Activity activity, RuleCheckResult ruleCheckResult, SimilarSearchResult similarResult) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("你是校园活动平台的审核辅助助手。")
                .append("你不能直接决定活动是否发布，只能给平台管理员提供参考建议。")
                .append("当前活动提交者已经通过平台审核，具备活动主办方资格。")
                .append("除非活动内容本身明确冒用他人身份或主办方信息自相矛盾，否则不要把“主办方没有资质/主办方资质不足”作为问题或风险。")
                .append("请严格输出 JSON，不要输出额外解释。")
                .append('\n')
                .append("输出 JSON 字段固定为：")
                .append("{\"suggestion\":\"PASS/MANUAL_REVIEW/REJECT\",\"riskLevel\":\"LOW/MEDIUM/HIGH\",\"score\":0,")
                .append("\"problems\":[\"...\"],\"missingFields\":[\"...\"],\"similarActivities\":[{\"title\":\"...\"}],")
                .append("\"similarityAnalysis\":\"...\",\"reviewComment\":\"...\"}")
                .append('\n')
                .append("审核维度：信息完整性、内容一致性、风险内容、表达质量、相似活动。")
                .append('\n')
                .append("活动标题：").append(StrUtil.blankToDefault(activity.getTitle(), "未填写")).append('\n')
                .append("活动简介：").append(StrUtil.blankToDefault(activity.getSummary(), "未填写")).append('\n')
                .append("活动详情：").append(StrUtil.blankToDefault(activity.getContent(), "未填写")).append('\n')
                .append("活动分类：").append(StrUtil.blankToDefault(activity.getCategory(), "未填写")).append('\n')
                .append("主办方联系方式：").append(StrUtil.blankToDefault(activity.getContactInfo(), "未填写")).append('\n')
                .append("活动地点：").append(StrUtil.blankToDefault(activity.getLocation(), "未填写")).append('\n')
                .append("主办方：").append(StrUtil.blankToDefault(activity.getOrganizerName(), "未填写")).append('\n')
                .append("人数上限：").append(activity.getMaxParticipants() == null ? "未填写" : activity.getMaxParticipants()).append('\n')
                .append("报名时间：").append(formatTime(activity.getRegistrationStartTime()))
                .append(" - ").append(formatTime(activity.getRegistrationEndTime())).append('\n')
                .append("活动时间：").append(formatTime(activity.getEventStartTime()))
                .append(" - ").append(formatTime(activity.getEventEndTime())).append('\n')
                .append("规则校验问题：").append(JSONUtil.toJsonStr(ruleCheckResult.problems)).append('\n')
                .append("规则校验缺失字段：").append(JSONUtil.toJsonStr(ruleCheckResult.missingFields)).append('\n')
                .append("历史相似活动：").append(JSONUtil.toJsonStr(similarResult.activities)).append('\n')
                .append("相似活动补充说明：").append(StrUtil.blankToDefault(similarResult.note, "无"));
        return builder.toString();
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "未填写" : value.format(TIME_FORMATTER);
    }

    private static class RuleCheckResult {
        private final List<String> problems = new ArrayList<>();
        private final List<String> missingFields = new ArrayList<>();
    }

    private static class SimilarSearchResult {
        private final List<Map<String, Object>> activities = new ArrayList<>();
        private String note;
    }
}
