package com.jacolp.framework.elasticsearch;

import java.util.Objects;

final class DefaultElasticsearchIndexResolver implements ElasticsearchIndexResolver {

    private final ElasticsearchProperties properties;

    /** 保存配置对象；实际索引解析仍延迟到调用 {@link #requireIndex(String)} 时执行。 */
    DefaultElasticsearchIndexResolver(ElasticsearchProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 将业务使用的逻辑索引名解析为配置中的物理索引名。 */
    @Override
    public String requireIndex(String logicalIndexName) {
        // 逻辑名必须先经过校验，避免把空值拼接进配置键或下游 Elasticsearch 请求。
        if (logicalIndexName == null || logicalIndexName.isBlank()) {
            throw new ElasticsearchOperationException("logical Elasticsearch index name must not be blank");
        }
        // 物理索引由部署配置决定，组件不替业务模块猜测默认索引或自动创建索引。
        String index = properties.getIndex().get(logicalIndexName);
        if (index == null || index.isBlank()) {
            throw new ElasticsearchOperationException(
                    "jacolp.elasticsearch.index.%s must be configured".formatted(logicalIndexName));
        }
        return index;
    }
}
