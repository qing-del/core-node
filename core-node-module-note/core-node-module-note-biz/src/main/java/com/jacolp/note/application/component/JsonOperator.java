package com.jacolp.note.application.component;

import java.util.List;
import java.util.Map;

import com.jacolp.common.core.json.JacksonObjectMapper;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON 操作工具 Bean —— 包装 {@link ObjectMapper}，提供常用序列化/反序列化快捷方法。
 *
 * <p>作为单例注入到 Spring 容器中，底层使用 {@link JacksonObjectMapper} 实例。
 * 需要自定义序列化逻辑时可通过 {@link #getObjectMapper()} 获取原始 Mapper。</p>
 *
 * <pre>
 * // 序列化为紧凑 JSON
 * String json = jsonOperator.toJson(obj);
 *
 * // 序列化为格式化 JSON（适合存入数据库）
 * String prettyJson = jsonOperator.jsonToString(obj);
 *
 * // 反序列化为对象
 * User user = jsonOperator.fromJson(json, User.class);
 *
 * // Map → JSON
 * String mapJson = jsonOperator.mapToJson(Map.of("key", "value"));
 * </pre>
 */
@Component
public class JsonOperator {

    private final ObjectMapper objectMapper;

    public JsonOperator() {
        this.objectMapper = new JacksonObjectMapper();
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 获取底层 {@link ObjectMapper}，供需要自定义配置的调用方使用。
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    // ==================== 序列化 ====================

    /**
     * 将 Java 对象序列化为紧凑 JSON 字符串。
     * @param obj 待序列化对象
     * @return JSON 字符串，失败时返回 "{}" 或 "[]"
     */
    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj instanceof List || obj != null && obj.getClass().isArray() ? "[]" : "{}";
        }
    }

    /**
     * 将 Java 对象序列化为格式化的 JSON 字符串（带缩进与换行），适合存入数据库。
     * @param obj 待序列化对象
     * @return 格式化 JSON 字符串，失败时返回 "{}" 或 "[]"
     */
    public String jsonToString(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj instanceof List || obj != null && obj.getClass().isArray() ? "[]" : "{}";
        }
    }

    /**
     * 将 {@link Map} 直接序列化为紧凑 JSON 字符串。
     * @param map 待序列化的 Map
     * @return JSON 字符串
     */
    public String mapToJson(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return toJson(map);
    }

    // ==================== 反序列化 ====================

    /**
     * 将 JSON 字符串反序列化为指定类型的 Java 对象。
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @return 反序列化后的对象，失败时返回 null
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为泛型类型（如 {@code List<String>}、{@code Map<String, Object>}）。
     * <pre>{@code
     * List<String> tags = jsonOperator.fromJson(json, new TypeReference<List<String>>() {});
     * }</pre>
     * @param json    JSON 字符串
     * @param typeRef 泛型类型引用
     * @return 反序列化后的对象，失败时返回 null
     */
    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为 {@code Map<String, Object>}。
     * @param json JSON 字符串
     * @return Map 对象，失败时返回空 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
