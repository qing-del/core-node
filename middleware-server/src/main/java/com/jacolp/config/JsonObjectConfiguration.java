package com.jacolp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jacolp.json.JacksonObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonObjectConfiguration {

    /**
     * 全局 ObjectMapper Bean —— 基于 {@link JacksonObjectMapper}，
     * 追加 NON_NULL 序列化策略和禁用时间戳输出。
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        JacksonObjectMapper objectMapper = new JacksonObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setDefaultPropertyInclusion(
                JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
        return objectMapper;
    }
}
