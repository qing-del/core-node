package com.jacolp.document.application.fileindex;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import com.jacolp.framework.elasticsearch.ElasticsearchIndexResolver;
import com.jacolp.framework.elasticsearch.ElasticsearchOperationException;
import com.jacolp.framework.elasticsearch.ElasticsearchOperations;
import java.io.IOException;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 仅负责创建 file 物理索引及其固定 mapping，不执行任何数据写入。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "enabled", havingValue = "true")
public class FileIndexMappingInitializer implements ApplicationRunner {

    /** file 在共享 ES 配置中的逻辑索引名。 */
    public static final String LOGICAL_INDEX_NAME = "file";

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchIndexResolver indexResolver;

    /** 保存 ES 原始客户端和框架通用操作，mapping 必须由业务模块显式声明。 */
    public FileIndexMappingInitializer(ElasticsearchClient elasticsearchClient,
                                       ElasticsearchOperations elasticsearchOperations,
                                       ElasticsearchIndexResolver indexResolver) {
        this.elasticsearchClient = Objects.requireNonNull(elasticsearchClient, "elasticsearchClient must not be null");
        this.elasticsearchOperations = Objects.requireNonNull(elasticsearchOperations,
                "elasticsearchOperations must not be null");
        this.indexResolver = Objects.requireNonNull(indexResolver, "indexResolver must not be null");
    }

    /** 启动时只检查并创建缺失索引；已有索引的 mapping 不在应用启动时隐式修改。 */
    @Override
    public void run(ApplicationArguments args) {
        ensureInitialized();
    }

    /** 供全量重建入口在清空数据前确保物理索引存在。 */
    public void ensureInitialized() {
        String indexName = indexResolver.requireIndex(LOGICAL_INDEX_NAME);
        if (elasticsearchOperations.indexExists(indexName)) {
            return;
        }
        try {
            elasticsearchClient.indices().create(request -> request
                    .index(indexName)
                    .mappings(mapping -> mapping
                            .properties("fileName", textWithKeyword())
                            .properties("resourceType", keyword())
                            .properties("resourceId", keyword())
                            .properties("resourceUrl", keywordNotIndexed())
                            .properties("visibleUserIds", keyword())
                            .properties("isPublic", booleanProperty())
                            .properties("isDelete", booleanProperty())));
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not create file Elasticsearch index mapping", exception);
        }
    }

    /** fileName 使用 text 主字段并保留 keyword 子字段，支持名称提示的精确前缀检索。 */
    private static Property textWithKeyword() {
        return Property.of(property -> property.text(text -> text
                .fields("keyword", Property.of(keyword -> keyword.keyword(keywordProperty -> keywordProperty)))));
    }

    /** 创建默认可检索的 keyword 字段。 */
    private static Property keyword() {
        return Property.of(property -> property.keyword(keyword -> keyword));
    }

    /** 创建保留 source 但关闭倒排索引的 URL 字段。 */
    private static Property keywordNotIndexed() {
        return Property.of(property -> property.keyword(keyword -> keyword.index(false)));
    }

    /** 创建布尔字段。 */
    private static Property booleanProperty() {
        return Property.of(property -> property.boolean_(booleanProperty -> booleanProperty));
    }
}
