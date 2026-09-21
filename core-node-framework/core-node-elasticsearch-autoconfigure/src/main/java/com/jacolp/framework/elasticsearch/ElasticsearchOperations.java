package com.jacolp.framework.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.Optional;

/**
 * Common synchronous Elasticsearch document operations for modules that do not need the raw client.
 *
 * <p>Index mappings, analyzers, aliases, and business query construction remain the responsibility
 * of the owning module. The official {@code ElasticsearchClient} is still exposed for those advanced
 * cases.</p>
 */
public interface ElasticsearchOperations {

    /** 判断物理索引是否存在。 */
    boolean indexExists(String indexName);

    /** 创建物理索引，不自动推断业务映射。 */
    void createIndex(String indexName);

    /** 写入指定文档并返回写入结果。 */
    <T> ElasticsearchWriteResult index(String indexName, String documentId, T document);

    /** 读取指定文档，不存在时返回空结果。 */
    <T> Optional<T> get(String indexName, String documentId, Class<T> documentType);

    /** 删除指定文档，并返回是否确实删除了现有文档。 */
    boolean delete(String indexName, String documentId);

    /** 按 from/size 执行分页查询并返回 source 命中。 */
    <T> ElasticsearchSearchPage<T> search(String indexName, Query query, int from, int size,
                                           Class<T> documentType);
}
