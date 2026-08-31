package com.campus.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@Configuration
public class ActivityAiModelConfig {

    @Bean
    @ConditionalOnMissingBean(ObservationRegistry.class)
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.ai.openai.api-key:}')")
    public OpenAiApi openAiApi(Environment environment, RestClient.Builder restClientBuilder) {
        return OpenAiApi.builder()
                .baseUrl(environment.getProperty("spring.ai.openai.base-url", "https://dashscope.aliyuncs.com/compatible-mode"))
                .apiKey(environment.getProperty("spring.ai.openai.api-key"))
                .completionsPath("/v1/chat/completions")
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.ai.openai.api-key:}')")
    public OpenAiChatModel openAiChatModel(Environment environment,
                                           ObservationRegistry observationRegistry,
                                           OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(environment.getProperty("spring.ai.openai.chat.options.model", "qwen-plus"))
                .temperature(0.1D)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.ai.openai.api-key:}')")
    public ChatClient chatClient(OpenAiChatModel openAiChatModel, ObservationRegistry observationRegistry) {
        return ChatClient.create(openAiChatModel, observationRegistry);
    }
}
