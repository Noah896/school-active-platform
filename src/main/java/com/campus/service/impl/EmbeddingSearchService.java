package com.campus.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.config.ActivitySearchProperties;
import com.campus.config.EmbeddingProperties;
import com.campus.dto.ActivityVectorRecallDTO;
import com.campus.entity.Activity;
import com.campus.entity.ActivityTag;
import com.campus.entity.ActivityTagRelation;
import com.campus.mapper.ActivityMapper;
import com.campus.mapper.ActivityTagMapper;
import com.campus.mapper.ActivityTagRelationMapper;
import com.campus.service.ActivitySearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmbeddingSearchService {

    private static final int STATUS_PUBLISHED = 2;
    private static final double DEFAULT_MIN_SIMILARITY = 0.60D;
    private static final int DEFAULT_TOP_N = 50;

    @Resource
    private ActivitySearchProperties activitySearchProperties;

    @Resource
    private EmbeddingProperties embeddingProperties;

    @Resource
    private ActivityMapper activityMapper;

    @Resource
    private ActivityTagRelationMapper activityTagRelationMapper;

    @Resource
    private ActivityTagMapper activityTagMapper;

    @Resource
    private ActivitySearchService activitySearchService;

    private final RestHighLevelClient restHighLevelClient;
    private volatile boolean embeddingMappingReady;

    public EmbeddingSearchService(ObjectProvider<RestHighLevelClient> clientProvider) {
        this.restHighLevelClient = clientProvider.getIfAvailable();
    }

    @PostConstruct
    public void initVectorMapping() {
        if (!isAvailable()) {
            return;
        }
        try {
            ensureIndexExists();
            ensureEmbeddingMapping();
        } catch (Exception e) {
            log.warn("初始化活动向量索引字段失败", e);
        }
    }

    public boolean isAvailable() {
        return activitySearchProperties.getEs().isEnabled() && restHighLevelClient != null;
    }

    public void upsertActivityEmbedding(Long activityId, String embeddingText, float[] vector, String modelName) {
        if (!isAvailable() || activityId == null) {
            return;
        }
        try {
            ensureIndexExists();
            ensureEmbeddingMapping();
            Activity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                deleteActivityDocument(activityId);
                return;
            }
            IndexRequest request = new IndexRequest(indexName())
                    .id(String.valueOf(activityId))
                    .source(buildDocument(activity, embeddingText, vector, modelName));
            restHighLevelClient.index(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new IllegalStateException("写入活动向量索引失败", e);
        }
    }

    public void deleteActivityEmbedding(Long activityId) {
        if (!isAvailable() || activityId == null) {
            return;
        }
        try {
            ensureIndexExists();
            ensureEmbeddingMapping();
            restHighLevelClient.delete(new DeleteRequest(indexName(), String.valueOf(activityId)), RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.warn("删除活动向量索引失败 activityId={}", activityId, e);
        }
    }

    public List<ActivityVectorRecallDTO> searchSimilarActivities(float[] queryVector, int topN, double minSimilarity) {
        if (!isAvailable() || queryVector == null || queryVector.length == 0) {
            return Collections.emptyList();
        }
        try {
            ensureIndexExists();
            ensureEmbeddingMapping();
            int size = Math.max(1, Math.min(topN <= 0 ? DEFAULT_TOP_N : topN, 100));
            double threshold = minSimilarity <= 0D ? DEFAULT_MIN_SIMILARITY : minSimilarity;
            SearchRequest request = new SearchRequest(indexName());
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.size(size);
            sourceBuilder.fetchSource(false);
            sourceBuilder.query(QueryBuilders.scriptScoreQuery(
                    QueryBuilders.boolQuery()
                            .filter(QueryBuilders.termQuery("status", STATUS_PUBLISHED))
                            .filter(QueryBuilders.existsQuery("embeddingVector")),
                    new Script(
                            ScriptType.INLINE,
                            "painless",
                            "cosineSimilarity(params.queryVector, 'embeddingVector') + 1.0",
                            Map.of("queryVector", toVectorList(queryVector))
                    )
            ));
            request.source(sourceBuilder);
            SearchHits hits = restHighLevelClient.search(request, RequestOptions.DEFAULT).getHits();
            if (hits == null || hits.getHits().length == 0) {
                return Collections.emptyList();
            }
            List<ActivityVectorRecallDTO> result = new ArrayList<>(hits.getHits().length);
            for (SearchHit hit : hits.getHits()) {
                double similarity = Math.max(0D, hit.getScore() - 1.0D);
                if (similarity < threshold) {
                    continue;
                }
                result.add(new ActivityVectorRecallDTO(Long.valueOf(hit.getId()), round(similarity)));
            }
            return result;
        } catch (Exception e) {
            log.warn("活动向量召回失败", e);
            return Collections.emptyList();
        }
    }

    private void ensureIndexExists() throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(indexName());
        if (restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT)) {
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
        request.mapping(buildBaseMapping());
        restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
    }

    private void ensureEmbeddingMapping() throws IOException {
        if (embeddingMappingReady && hasDenseVectorMapping()) {
            return;
        }
        if (hasIncompatibleEmbeddingVectorMapping()) {
            log.warn("检测到 activity_index_dev 的 embeddingVector 映射不是 dense_vector，开始重建索引");
            activitySearchService.rebuildIndexFromMysql();
        }
        PutMappingRequest request = new PutMappingRequest(indexName());
        request.source(buildEmbeddingMapping());
        restHighLevelClient.indices().putMapping(request, RequestOptions.DEFAULT);
        if (!hasDenseVectorMapping()) {
            throw new IllegalStateException("embeddingVector 字段映射不是 dense_vector");
        }
        embeddingMappingReady = true;
    }

    private boolean hasDenseVectorMapping() throws IOException {
        return "dense_vector".equals(readEmbeddingVectorFieldType());
    }

    private boolean hasIncompatibleEmbeddingVectorMapping() throws IOException {
        String fieldType = readEmbeddingVectorFieldType();
        return StrUtil.isNotBlank(fieldType) && !"dense_vector".equals(fieldType);
    }

    private String readEmbeddingVectorFieldType() throws IOException {
        GetMappingsRequest request = new GetMappingsRequest().indices(indexName());
        GetMappingsResponse response = restHighLevelClient.indices().getMapping(request, RequestOptions.DEFAULT);
        MappingMetadata metadata = response.mappings().get(indexName());
        if (metadata == null || metadata.getSourceAsMap() == null) {
            return null;
        }
        Object properties = metadata.getSourceAsMap().get("properties");
        if (!(properties instanceof Map<?, ?> propertiesMap)) {
            return null;
        }
        Object embeddingVector = propertiesMap.get("embeddingVector");
        if (!(embeddingVector instanceof Map<?, ?> embeddingVectorMap)) {
            return null;
        }
        Object fieldType = embeddingVectorMap.get("type");
        return fieldType == null ? null : String.valueOf(fieldType);
    }

    private XContentBuilder buildBaseMapping() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.startObject("properties");
        builder.startObject("creatorId").field("type", "long").endObject();
        builder.startObject("title").field("type", "text").field("analyzer", "campus_text_analyzer")
                .field("search_analyzer", "standard").endObject();
        builder.startObject("summary").field("type", "text").field("analyzer", "campus_text_analyzer")
                .field("search_analyzer", "standard").endObject();
        builder.startObject("content").field("type", "text").field("analyzer", "campus_text_analyzer")
                .field("search_analyzer", "standard").endObject();
        builder.startObject("tags").field("type", "text").field("analyzer", "campus_text_analyzer")
                .field("search_analyzer", "standard").endObject();
        builder.startObject("tagNames").field("type", "keyword").endObject();
        builder.startObject("organizerName").field("type", "text").field("analyzer", "campus_text_analyzer")
                .field("search_analyzer", "standard").endObject();
        builder.startObject("location").field("type", "text").field("analyzer", "campus_text_analyzer")
                .field("search_analyzer", "standard").endObject();
        builder.startObject("category").field("type", "keyword").endObject();
        builder.startObject("registrationMode").field("type", "keyword").endObject();
        builder.startObject("status").field("type", "integer").endObject();
        builder.startObject("stageStatus").field("type", "keyword").endObject();
        builder.startObject("registrationStartTime").field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss").endObject();
        builder.startObject("registrationEndTime").field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss").endObject();
        builder.startObject("eventStartTime").field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss").endObject();
        builder.startObject("eventEndTime").field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss").endObject();
        builder.startObject("createTime").field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss").endObject();
        builder.startObject("updateTime").field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss").endObject();
        builder.startObject("registeredCount").field("type", "integer").endObject();
        builder.startObject("maxParticipants").field("type", "integer").endObject();
        builder.startObject("heatScore").field("type", "long").endObject();
        builder.startObject("isHot").field("type", "boolean").endObject();
        builder.endObject();
        builder.endObject();
        return builder;
    }

    private XContentBuilder buildEmbeddingMapping() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.startObject("properties");
        builder.startObject("embeddingText").field("type", "text").field("index", false).endObject();
        builder.startObject("embeddingVector")
                .field("type", "dense_vector")
                .field("dims", embeddingProperties.getDimensions())
                .endObject();
        builder.startObject("embeddingModel").field("type", "keyword").endObject();
        builder.startObject("embeddingUpdatedAt").field("type", "date")
                .field("format", "strict_date_optional_time||yyyy-MM-dd HH:mm:ss").endObject();
        builder.endObject();
        builder.endObject();
        return builder;
    }

    private Map<String, Object> buildDocument(Activity activity, String embeddingText, float[] vector, String modelName) {
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
        document.put("embeddingText", StrUtil.blankToDefault(embeddingText, ""));
        document.put("embeddingModel", StrUtil.blankToDefault(modelName, ""));
        document.put("embeddingUpdatedAt", LocalDateTime.now().toString());
        if (vector != null && vector.length > 0) {
            document.put("embeddingVector", toVectorList(vector));
        }
        return document;
    }

    private void deleteActivityDocument(Long activityId) throws IOException {
        restHighLevelClient.delete(new org.elasticsearch.action.delete.DeleteRequest(indexName(), String.valueOf(activityId)),
                RequestOptions.DEFAULT);
    }

    private String indexName() {
        return activitySearchProperties.getEs().getIndexName();
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

    private String resolveStageStatus(Activity activity) {
        if (!Objects.equals(activity.getStatus(), STATUS_PUBLISHED)) {
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
            long hours = Math.max(java.time.Duration.between(activity.getCreateTime(), LocalDateTime.now()).toHours(), 0L);
            freshnessWeight = Math.max(72L - hours, 0L);
        }
        return registeredWeight + capacityWeight + freshnessWeight;
    }

    private Integer defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private List<Float> toVectorList(float[] vector) {
        if (vector == null || vector.length == 0) {
            return Collections.emptyList();
        }
        List<Float> result = new ArrayList<>(vector.length);
        for (float item : vector) {
            result.add(item);
        }
        return result;
    }

    private double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }
}
