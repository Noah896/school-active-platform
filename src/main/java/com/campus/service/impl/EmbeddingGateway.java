package com.campus.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.campus.config.EmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class EmbeddingGateway {

    private final RestClient restClient;
    private final EmbeddingProperties embeddingProperties;
    private final Environment environment;

    public EmbeddingGateway(RestClient.Builder restClientBuilder,
                            EmbeddingProperties embeddingProperties,
                            Environment environment) {
        this.embeddingProperties = embeddingProperties;
        this.environment = environment;
        this.restClient = restClientBuilder.build();
    }

    public boolean isAvailable() {
        return embeddingProperties.isEnabled() && StrUtil.isNotBlank(apiKey());
    }

    public String modelName() {
        return StrUtil.blankToDefault(embeddingProperties.getModel(), "text-embedding-v4");
    }

    public int dimensions() {
        return Math.max(1, embeddingProperties.getDimensions());
    }

    public float[] embed(String text) {
        if (!isAvailable() || StrUtil.isBlank(text)) {
            return null;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", modelName());
        request.put("input", text);
        request.put("dimensions", dimensions());
        request.put("encoding_format", "float");
        String response = restClient.post()
                .uri(embeddingUri())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey())
                .body(request)
                .retrieve()
                .body(String.class);
        if (StrUtil.isBlank(response)) {
            return null;
        }
        JSONObject json = JSONUtil.parseObj(response);
        if (json.containsKey("error")) {
            throw new IllegalStateException(StrUtil.blankToDefault(json.getJSONObject("error").getStr("message"), "embedding 调用失败"));
        }
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return null;
        }
        JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.getFloat(i);
        }
        return vector;
    }

    private String embeddingUri() {
        String baseUrl = environment.getProperty("spring.ai.openai.base-url", "https://dashscope.aliyuncs.com/compatible-mode");
        return StrUtil.addSuffixIfNot(baseUrl, "/") + "v1/embeddings";
    }

    private String apiKey() {
        return environment.getProperty("spring.ai.openai.api-key");
    }
}
