package com.campus.config;

import cn.hutool.core.util.StrUtil;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "campus.activity.search.es", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestHighLevelClient restHighLevelClient(ActivitySearchProperties properties) {
        ActivitySearchProperties.Es es = properties.getEs();
        HttpHost[] hosts = Arrays.stream(StrUtil.splitToArray(es.getUris(), ","))
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(hosts)
                .setDefaultHeaders(new Header[]{new BasicHeader("Content-Type", "application/json")})
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(es.getConnectTimeoutMillis())
                        .setSocketTimeout(es.getSocketTimeoutMillis()));

        if (StrUtil.isNotBlank(es.getUsername())) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(es.getUsername(), es.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        return new RestHighLevelClient(builder);
    }
}
