package com.campus.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.config.ActivitySearchProperties;
import com.campus.config.EmbeddingProperties;
import com.campus.dto.ActivitySearchPageDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityTag;
import com.campus.entity.ActivityTagRelation;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.service.ActivitySearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ActivitySearchServiceImpl implements ActivitySearchService {

    private static final int STATUS_PUBLISHED = 2;
    private static final int STATUS_OFFLINE_PENDING_REVIEW = 5;
    private static final String SORT_COMPOSITE = "composite";
    private static final String SORT_START_TIME_ASC = "startTimeAsc";
    private static final String SORT_PUBLISH_TIME_DESC = "publishTimeDesc";
    private static final String SORT_SIGNUP_COUNT_DESC = "signupCountDesc";
    private static final String SORT_HEAT_SCORE_DESC = "heatScoreDesc";

    @Resource
    private ActivityMapper activityMapper;

    @Resource
    private ActivitySearchProperties activitySearchProperties;

    @Resource
    private ActivityTagMapper activityTagMapper;

    @Resource
    private ActivityTagRelationMapper activityTagRelationMapper;

    @Resource
    private EmbeddingProperties embeddingProperties;

    private final RestHighLevelClient restHighLevelClient;

    private volatile LocalDateTime lastRepairTime;

    public ActivitySearchServiceImpl(ObjectProvider<RestHighLevelClient> clientProvider) {
        this.restHighLevelClient = clientProvider.getIfAvailable();
    }

    @PostConstruct
    public void initIndex() {
        if (!isAvailable()) {
            log.info("活动搜索 ES 未启用，公共列表将走 MySQL 降级链路");
            return;
        }
        try {
            initializeIndexWithRecovery();
        } catch (Exception e) {
            log.warn("初始化活动搜索索引失败，将保留 MySQL 降级能力", e);
        }
    }

    @Override
    public ActivitySearchPageDTO searchActivities(String keyword,
                                                  String category,
                                                  Integer status,
                                                  String location,
                                                  String organizerName,
                                                  String sortBy,
                                                  LocalDateTime startTimeFrom,
                                                  LocalDateTime startTimeTo,
                                                  Integer current,
                                                  Integer pageSize) {
        return searchActivitiesInternal(keyword, category, status, location, organizerName, sortBy,
                startTimeFrom, startTimeTo, null, true, current, pageSize);
    }

    @Override
    public ActivitySearchPageDTO searchActivitiesByCreator(Long creatorId,
                                                           String keyword,
                                                           Integer current,
                                                           Integer pageSize) {
        return searchActivitiesInternal(keyword, null, null, null, null, StrUtil.isBlank(keyword) ? SORT_PUBLISH_TIME_DESC : SORT_COMPOSITE,
                null, null, creatorId, false, current, pageSize);
    }

    @Override
    public ActivitySearchPageDTO searchActivitiesByKeyword(String keyword,
                                                           Integer current,
                                                           Integer pageSize) {
        return searchActivitiesInternal(keyword, null, null, null, null, SORT_COMPOSITE,
                null, null, null, false, current, pageSize);
    }

    @Override
    public List<String> queryCategories(Integer status) {
        assertClientAvailable();
        try {
            ensureIndex();
            SearchRequest request = new SearchRequest(indexName());
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.size(0);
            sourceBuilder.query(buildCategoryQuery(status));
            sourceBuilder.aggregation(AggregationBuilders.terms("categoryAgg")
                    .field("category")
                    .size(100)
                    .order(BucketOrder.key(true)));
            request.source(sourceBuilder);
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            ParsedStringTerms aggregation = response.getAggregations().get("categoryAgg");
            if (aggregation == null) {
                return Collections.emptyList();
            }
            return aggregation.getBuckets().stream()
                    .map(Terms.Bucket::getKeyAsString)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new IllegalStateException("ES 活动分类聚合失败", e);
        }
    }

    @Override
    public void syncActivity(Long activityId) {
        if (!isAvailable() || activityId == null) {
            return;
        }
        try {
            ensureIndex();
            Activity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                restHighLevelClient.delete(new DeleteRequest(indexName(), String.valueOf(activityId)), RequestOptions.DEFAULT);
                return;
            }
            IndexRequest request = new IndexRequest(indexName())
                    .id(String.valueOf(activityId))
                    .source(buildDocument(activity));
            restHighLevelClient.index(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("同步活动搜索索引失败 activityId={}", activityId, e);
            throw new IllegalStateException("同步活动索引失败", e);
        }
    }

    @Override
    public void rebuildIndexFromMysql() {
        if (!isAvailable()) {
            return;
        }
        try {
            ensureIndex();
            rebuildIndex();
            bootstrapIndexIfEmpty();
        } catch (Exception e) {
            throw new IllegalStateException("重建活动索引失败", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return activitySearchProperties.getEs().isEnabled() && restHighLevelClient != null;
    }

    @Scheduled(cron = "${campus.activity.search.es.repair-cron:0 */10 * * * ?}")
    public void repairIndexIncrementally() {
        if (!isAvailable()) {
            return;
        }
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            ensureIndex();
            int pageSize = Math.max(activitySearchProperties.getEs().getRepairPageSize(), 50);
            long current = 1L;
            while (true) {
                QueryWrapper<Activity> wrapper = new QueryWrapper<>();
                LocalDateTime lowerBound = resolveRepairLowerBound();
                if (lowerBound != null) {
                    wrapper.ge("update_time", lowerBound);
                }
                wrapper.orderByAsc("update_time").orderByAsc("id");
                Page<Activity> page = new Page<>(current, pageSize);
                activityMapper.selectPage(page, wrapper);
                if (CollUtil.isEmpty(page.getRecords())) {
                    break;
                }
                for (Activity activity : page.getRecords()) {
                    try {
                        syncActivity(activity.getId());
                    } catch (Exception e) {
                        log.warn("修复活动搜索索引失败 activityId={}", activity.getId(), e);
                    }
                }
                if (current >= page.getPages()) {
                    break;
                }
                current++;
            }
            lastRepairTime = startedAt;
        } catch (Exception e) {
            log.error("活动搜索索引增量修复失败", e);
        }
    }

    private void bootstrapIndexIfEmpty() throws IOException {
        CountRequest countRequest = new CountRequest(indexName());
        CountResponse countResponse = restHighLevelClient.count(countRequest, RequestOptions.DEFAULT);
        if (countResponse.getCount() > 0) {
            return;
        }
        log.info("活动搜索索引为空，开始执行启动全量同步 index={}", indexName());
        QueryWrapper<Activity> wrapper = new QueryWrapper<Activity>()
                .orderByAsc("id");
        List<Activity> activities = activityMapper.selectList(wrapper);
        for (Activity activity : activities) {
            syncActivity(activity.getId());
        }
        log.info("活动搜索索引启动全量同步完成，总计 {} 条", activities.size());
    }

    private void initializeIndexWithRecovery() throws IOException {
        try {
            ensureIndex();
            bootstrapIndexIfEmpty();
        } catch (Exception e) {
            if (!isBrokenIndexException(e)) {
                throw e;
            }
            log.warn("检测到活动搜索索引异常，准备删除并重建 index={}", indexName(), e);
            rebuildIndex();
            bootstrapIndexIfEmpty();
        }
    }

    private LocalDateTime resolveRepairLowerBound() {
        if (lastRepairTime != null) {
            return lastRepairTime.minusMinutes(5);
        }
        int lookbackHours = activitySearchProperties.getEs().getRepairLookbackHours();
        return lookbackHours <= 0 ? null : LocalDateTime.now().minusHours(lookbackHours);
    }

    private List<Activity> loadActivitiesInOrder(List<Long> ids, boolean publicOnly) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<Activity> activities = activityMapper.selectBatchIds(ids);
        if (CollUtil.isEmpty(activities)) {
            return Collections.emptyList();
        }
        Map<Long, Activity> activityMap = activities.stream()
                .filter(item -> !publicOnly || isPublicActivityStatus(item.getStatus()))
                .collect(Collectors.toMap(Activity::getId, item -> item, (a, b) -> a, HashMap::new));
        List<Activity> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Activity activity = activityMap.get(id);
            if (activity != null) {
                ordered.add(activity);
            }
        }
        return ordered;
    }

    private ActivitySearchPageDTO searchActivitiesInternal(String keyword,
                                                           String category,
                                                           Integer status,
                                                           String location,
                                                           String organizerName,
                                                           String sortBy,
                                                           LocalDateTime startTimeFrom,
                                                           LocalDateTime startTimeTo,
                                                           Long creatorId,
                                                           boolean publicOnly,
                                                           Integer current,
                                                           Integer pageSize) {
        assertClientAvailable();
        try {
            ensureIndex();//判断es里面是否有数据 有则直接返回 没有则创建
            int currentPage = current == null || current < 1 ? 1 : current;
            int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
            SearchRequest request = new SearchRequest(indexName());
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.query(buildSearchQuery(keyword, category, status, location, organizerName,
                    startTimeFrom, startTimeTo, creatorId, publicOnly));
            sourceBuilder.from((currentPage - 1) * size);
            sourceBuilder.size(size);
            sourceBuilder.trackTotalHits(true);//当数据超过10000条数据时，全部返回,而不是只返回10000条
            sourceBuilder.fetchSource(false);//只返回数量/id,不返回任何文档的原始数据
            applySort(sourceBuilder, sortBy, StrUtil.isNotBlank(keyword));
            request.source(sourceBuilder);//把前面封装好的查询条件封装到request中

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            List<Long> ids = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                ids.add(Long.valueOf(hit.getId()));
            }
            List<Activity> orderedActivities = loadActivitiesInOrder(ids, publicOnly);
            long total = response.getHits().getTotalHits() == null ? 0L : response.getHits().getTotalHits().value;
            return new ActivitySearchPageDTO(orderedActivities, total);
        } catch (Exception e) {
            throw new IllegalStateException("ES 活动搜索失败", e);
        }
    }

    private void assertClientAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException("ES 客户端不可用");
        }
    }

    private String indexName() {
        return activitySearchProperties.getEs().getIndexName();
    }

    private void ensureIndex() throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(indexName());
        boolean exists = restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
        if (exists) {
            return;
        }
        CreateIndexRequest request = new CreateIndexRequest(indexName());
        request.settings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.number_of_replicas", 0)
                .put("analysis.tokenizer.campus_ngram_tokenizer.type", "ngram")
                .put("analysis.tokenizer.campus_ngram_tokenizer.min_gram", 1)
                .put("analysis.tokenizer.campus_ngram_tokenizer.max_gram", 2)
                .putList("analysis.tokenizer.campus_ngram_tokenizer.token_chars", "letter", "digit")
                .putList("analysis.analyzer.campus_text_analyzer.filter", "lowercase")
                .put("analysis.analyzer.campus_text_analyzer.tokenizer", "campus_ngram_tokenizer"));
        request.mapping(buildMapping());
        restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
    }

    private void rebuildIndex() throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(indexName());
        boolean exists = restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
        if (exists) {
            restHighLevelClient.indices().delete(new DeleteIndexRequest(indexName()), RequestOptions.DEFAULT);
        }
        ensureIndex();
    }

    private boolean isBrokenIndexException(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ElasticsearchStatusException statusException) {
                String message = StrUtil.nullToEmpty(statusException.getMessage());
                if (containsBrokenIndexHint(message)) {
                    return true;
                }
            }
            String message = StrUtil.nullToEmpty(current.getMessage());
            if (containsBrokenIndexHint(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsBrokenIndexHint(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return StrUtil.containsAny(normalized,
                "all shards failed",
                "no_shard_available_action_exception",
                "unavailable_shards_exception");
    }

    private boolean isAsciiKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            if (keyword.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private XContentBuilder buildMapping() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.startObject("properties");
        appendLongField(builder, "creatorId");
        appendTextField(builder, "title");
        appendTextField(builder, "summary");
        appendTextField(builder, "content");
        appendTextField(builder, "tags");
        appendKeywordField(builder, "tagNames");
        appendTextField(builder, "organizerName");
        appendTextField(builder, "location");
        appendKeywordField(builder, "category");
        appendKeywordField(builder, "registrationMode");
        appendIntegerField(builder, "status");
        appendKeywordField(builder, "stageStatus");
        appendDateField(builder, "registrationStartTime");
        appendDateField(builder, "registrationEndTime");
        appendDateField(builder, "eventStartTime");
        appendDateField(builder, "eventEndTime");
        appendDateField(builder, "createTime");
        appendDateField(builder, "updateTime");
        appendIntegerField(builder, "registeredCount");
        appendIntegerField(builder, "maxParticipants");
        appendLongField(builder, "heatScore");
        builder.startObject("isHot").field("type", "boolean").endObject();
        builder.startObject("embeddingText").field("type", "text").field("index", false).endObject();
        builder.startObject("embeddingVector")
                .field("type", "dense_vector")
                .field("dims", Math.max(1, embeddingProperties.getDimensions()))
                .endObject();
        builder.startObject("embeddingModel").field("type", "keyword").endObject();
        appendDateField(builder, "embeddingUpdatedAt");
        builder.endObject();
        builder.endObject();
        return builder;
    }

    private void appendTextField(XContentBuilder builder, String fieldName) throws IOException {
        builder.startObject(fieldName);
        builder.field("type", "text");
        builder.field("analyzer", "campus_text_analyzer");
        builder.field("search_analyzer", "standard");
        builder.startObject("fields");
        builder.startObject("keyword");
        builder.field("type", "keyword");
        builder.field("ignore_above", 256);
        builder.endObject();
        builder.endObject();
        builder.endObject();
    }

    private void appendKeywordField(XContentBuilder builder, String fieldName) throws IOException {
        builder.startObject(fieldName).field("type", "keyword").endObject();
    }

    private void appendIntegerField(XContentBuilder builder, String fieldName) throws IOException {
        builder.startObject(fieldName).field("type", "integer").endObject();
    }

    private void appendLongField(XContentBuilder builder, String fieldName) throws IOException {
        builder.startObject(fieldName).field("type", "long").endObject();
    }

    private void appendDateField(XContentBuilder builder, String fieldName) throws IOException {
        builder.startObject(fieldName)
                .field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss")
                .endObject();
    }

    private BoolQueryBuilder buildSearchQuery(String keyword,
                                              String category,
                                              Integer status,
                                              String location,
                                              String organizerName,
                                              LocalDateTime startTimeFrom,
                                              LocalDateTime startTimeTo,
                                              Long creatorId,
                                              boolean publicOnly) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (creatorId != null) {
            boolQuery.filter(QueryBuilders.termQuery("creatorId", creatorId));
        }
        if (status == null && publicOnly) {
            boolQuery.filter(QueryBuilders.termsQuery("status", List.of(STATUS_PUBLISHED, STATUS_OFFLINE_PENDING_REVIEW)));
        } else if (status != null) {
            boolQuery.filter(QueryBuilders.termQuery("status", status));
        }
        if (StrUtil.isNotBlank(keyword)) {
            String normalizedKeyword = keyword.trim();
            MultiMatchQueryBuilder multiMatchQuery = QueryBuilders.multiMatchQuery(normalizedKeyword)
                    .field("title", 4.0f)
                    .field("tags", 3.0f)
                    .field("summary", 2.0f)
                    .field("organizerName", 2.0f)
                    .field("location")
                    .field("content")
                    .type(MultiMatchQueryBuilder.Type.BEST_FIELDS);
            BoolQueryBuilder keywordQuery = QueryBuilders.boolQuery()
                    .should(multiMatchQuery)
                    .should(QueryBuilders.matchPhraseQuery("title", normalizedKeyword).boost(6.0f))
                    .should(QueryBuilders.matchPhraseQuery("location", normalizedKeyword).boost(4.0f))
                    .minimumShouldMatch(1);
            if (isAsciiKeyword(normalizedKeyword)) {
                keywordQuery.should(QueryBuilders.wildcardQuery("title.keyword", "*" + normalizedKeyword + "*")
                        .caseInsensitive(true)
                        .boost(5.0f));
                keywordQuery.should(QueryBuilders.wildcardQuery("location.keyword", "*" + normalizedKeyword + "*")
                        .caseInsensitive(true)
                        .boost(4.0f));
                keywordQuery.should(QueryBuilders.matchQuery("title", normalizedKeyword).operator(Operator.AND).boost(3.0f));
                keywordQuery.should(QueryBuilders.matchQuery("location", normalizedKeyword).operator(Operator.AND).boost(3.0f));
            }
            boolQuery.must(keywordQuery);
        }
        if (StrUtil.isNotBlank(category)) {
            boolQuery.filter(QueryBuilders.termQuery("category", category));
        }
        if (StrUtil.isNotBlank(location)) {
            boolQuery.filter(QueryBuilders.termQuery("location.keyword", location));
        }
        if (StrUtil.isNotBlank(organizerName)) {
            boolQuery.filter(QueryBuilders.termQuery("organizerName.keyword", organizerName));
        }
        if (startTimeFrom != null || startTimeTo != null) {
            org.elasticsearch.index.query.RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("eventStartTime");
            if (startTimeFrom != null) {
                rangeQuery.gte(startTimeFrom.toString());
            }
            if (startTimeTo != null) {
                rangeQuery.lte(startTimeTo.toString());
            }
            boolQuery.filter(rangeQuery);
        }
        return boolQuery;
    }

    private BoolQueryBuilder buildCategoryQuery(Integer status) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.filter(QueryBuilders.termQuery("status", status == null ? STATUS_PUBLISHED : status));
        return boolQuery;
    }

    private void applySort(SearchSourceBuilder sourceBuilder, String sortBy, boolean hasKeyword) {
        String targetSort = StrUtil.blankToDefault(sortBy, SORT_COMPOSITE);
        List<SortBuilder<?>> sortBuilders = new ArrayList<>();
        switch (targetSort) {
            case SORT_START_TIME_ASC:
                sortBuilders.add(new FieldSortBuilder("eventStartTime").order(SortOrder.ASC).missing("_last"));
                sortBuilders.add(new FieldSortBuilder("createTime").order(SortOrder.DESC));
                break;
            case SORT_PUBLISH_TIME_DESC:
                sortBuilders.add(new FieldSortBuilder("createTime").order(SortOrder.DESC));
                break;
            case SORT_SIGNUP_COUNT_DESC:
                sortBuilders.add(new FieldSortBuilder("registeredCount").order(SortOrder.DESC));
                sortBuilders.add(new FieldSortBuilder("eventStartTime").order(SortOrder.ASC).missing("_last"));
                break;
            case SORT_HEAT_SCORE_DESC:
                sortBuilders.add(new FieldSortBuilder("heatScore").order(SortOrder.DESC));
                sortBuilders.add(new FieldSortBuilder("createTime").order(SortOrder.DESC));
                break;
            case SORT_COMPOSITE:
            default:
                if (hasKeyword) {
                    sortBuilders.add(new ScoreSortBuilder().order(SortOrder.DESC));
                    sortBuilders.add(new FieldSortBuilder("heatScore").order(SortOrder.DESC));
                    sortBuilders.add(new FieldSortBuilder("createTime").order(SortOrder.DESC));
                } else {
                    sortBuilders.add(new FieldSortBuilder("eventStartTime").order(SortOrder.ASC).missing("_last"));
                    sortBuilders.add(new FieldSortBuilder("heatScore").order(SortOrder.DESC));
                    sortBuilders.add(new FieldSortBuilder("createTime").order(SortOrder.DESC));
                }
                break;
        }
        for (SortBuilder<?> sortBuilder : sortBuilders) {
            sourceBuilder.sort(sortBuilder);
        }
    }

    private Map<String, Object> buildDocument(Activity activity) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("creatorId", activity.getCreatorId());
        document.put("title", StrUtil.blankToDefault(activity.getTitle(), ""));
        document.put("summary", StrUtil.blankToDefault(activity.getSummary(), ""));
        document.put("content", StrUtil.blankToDefault(activity.getContent(), ""));
        document.put("organizerName", StrUtil.blankToDefault(activity.getOrganizerName(), ""));
        document.put("category", StrUtil.blankToDefault(activity.getCategory(), ""));
        List<String> tagNames = queryActivityTagNames(activity.getId());
        document.put("tags", String.join(" ", tagNames));
        document.put("tagNames", tagNames);
        document.put("registrationMode", StrUtil.blankToDefault(activity.getRegistrationMode(), "AUDIT_REQUIRED"));
        document.put("location", StrUtil.blankToDefault(activity.getLocation(), ""));
        document.put("status", activity.getStatus());
        document.put("stageStatus", resolveStageStatus(activity));
        document.put("registrationStartTime", formatDate(activity.getRegistrationStartTime()));
        document.put("registrationEndTime", formatDate(activity.getRegistrationEndTime()));
        document.put("eventStartTime", formatDate(activity.getEventStartTime()));
        document.put("eventEndTime", formatDate(activity.getEventEndTime()));
        document.put("createTime", formatDate(activity.getCreateTime()));
        document.put("updateTime", formatDate(activity.getUpdateTime()));
        document.put("registeredCount", defaultNumber(activity.getRegisteredCount()));
        document.put("maxParticipants", defaultNumber(activity.getMaxParticipants()));
        long heatScore = calculateHeatScore(activity);
        document.put("heatScore", heatScore);
        document.put("isHot", heatScore >= 80L);
        return document;
    }

    private String resolveStageStatus(Activity activity) {
        if (!isPublicActivityStatus(activity.getStatus())) {
            return "OFFLINE";
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getRegistrationStartTime() != null && now.isBefore(activity.getRegistrationStartTime())) {
            return "REGISTRATION_NOT_STARTED";
        }
        if (activity.getRegistrationEndTime() != null && now.isBefore(activity.getRegistrationEndTime())) {
            return "REGISTRATION_OPEN";
        }
        if (activity.getEventStartTime() != null && now.isBefore(activity.getEventStartTime())) {
            return "UPCOMING";
        }
        if (activity.getEventEndTime() != null && now.isAfter(activity.getEventEndTime())) {
            return "FINISHED";
        }
        return "IN_PROGRESS";
    }

    private long calculateHeatScore(Activity activity) {
        long registeredWeight = defaultNumber(activity.getRegisteredCount()) * 10L;
        long capacityWeight = defaultNumber(activity.getMaxParticipants());
        long freshnessWeight = 0L;
        if (activity.getCreateTime() != null) {
            long hours = Math.max(Duration.between(activity.getCreateTime(), LocalDateTime.now()).toHours(), 0L);
            freshnessWeight = Math.max(72L - hours, 0L);
        }
        return registeredWeight + capacityWeight + freshnessWeight;
    }

    private Integer defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isPublicActivityStatus(Integer status) {
        return Objects.equals(status, STATUS_PUBLISHED)
                || Objects.equals(status, STATUS_OFFLINE_PENDING_REVIEW);
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private List<String> queryActivityTagNames(Long activityId) {
        if (activityId == null) {
            return Collections.emptyList();
        }
        List<ActivityTagRelation> relations = activityTagRelationMapper.selectList(new QueryWrapper<ActivityTagRelation>()
                .eq("activity_id", activityId)
                .orderByAsc("id"));
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyList();
        }
        List<Long> tagIds = relations.stream().map(ActivityTagRelation::getTagId).distinct().collect(Collectors.toList());
        Map<Long, String> tagNameMap = activityTagMapper.selectList(new QueryWrapper<ActivityTag>()
                        .in("id", tagIds)
                        .eq("status", 1))
                .stream()
                .collect(Collectors.toMap(ActivityTag::getId, ActivityTag::getName, (a, b) -> a));
        List<String> result = new ArrayList<>();
        for (ActivityTagRelation relation : relations) {
            String name = tagNameMap.get(relation.getTagId());
            if (StrUtil.isNotBlank(name)) {
                result.add(name);
            }
        }
        return result;
    }
}
