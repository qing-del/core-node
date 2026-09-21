package com.jacolp.framework.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class DefaultElasticsearchOperations implements ElasticsearchOperations {

    private final ElasticsearchClient client;

    /** 保存官方客户端实例；所有通用操作复用同一个应用级连接池。 */
    DefaultElasticsearchOperations(ElasticsearchClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /** 判断指定物理索引是否存在，并将 SDK 的 IO 异常转换为框架异常。 */
    @Override
    public boolean indexExists(String indexName) {
        validateIndexName(indexName);
        try {
            return client.indices().exists(request -> request.index(indexName)).value();
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not determine whether Elasticsearch index exists", exception);
        }
    }

    /** 创建指定物理索引；索引映射和别名由业务模块自行负责。 */
    @Override
    public void createIndex(String indexName) {
        validateIndexName(indexName);
        try {
            client.indices().create(request -> request.index(indexName));
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not create Elasticsearch index", exception);
        }
    }

    /** 写入文档并返回 Elasticsearch 分配的版本与操作结果。 */
    @Override
    public <T> ElasticsearchWriteResult index(String indexName, String documentId, T document) {
        validateIndexName(indexName);
        validateDocumentId(documentId);
        Objects.requireNonNull(document, "document must not be null");
        try {
            IndexResponse response = client.index(request -> request.index(indexName).id(documentId).document(document));
            return new ElasticsearchWriteResult(response.id(), response.version(), response.result().jsonValue());
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not index Elasticsearch document", exception);
        }
    }

    /** 按文档 ID 读取文档；不存在时返回空 Optional。 */
    @Override
    public <T> Optional<T> get(String indexName, String documentId, Class<T> documentType) {
        validateIndexName(indexName);
        validateDocumentId(documentId);
        Objects.requireNonNull(documentType, "documentType must not be null");
        try {
            GetResponse<T> response = client.get(request -> request.index(indexName).id(documentId), documentType);
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not get Elasticsearch document", exception);
        }
    }

    /** 删除文档，并用返回值区分实际删除和文档不存在。 */
    @Override
    public boolean delete(String indexName, String documentId) {
        validateIndexName(indexName);
        validateDocumentId(documentId);
        try {
            DeleteResponse response = client.delete(request -> request.index(indexName).id(documentId));
            return response.result() == Result.Deleted;
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not delete Elasticsearch document", exception);
        }
    }

    /** 执行分页查询，只把 source 命中转换为框架 DTO，不隐藏聚合等原始响应。 */
    @Override
    public <T> ElasticsearchSearchPage<T> search(String indexName, Query query, int from, int size,
                                                   Class<T> documentType) {
        validateIndexName(indexName);
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        if (from < 0) {
            throw new IllegalArgumentException("from must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        try {
            SearchResponse<T> response = client.search(request -> request.index(indexName).query(query)
                    .from(from).size(size), documentType);
            // 这里只转换带 source 的命中为框架 DTO；需要高亮、聚合等其他响应部分时，
            // 调用方仍可直接注入 ElasticsearchClient。
            List<ElasticsearchSearchHit<T>> hits = response.hits().hits().stream()
                    .map(hit -> new ElasticsearchSearchHit<>(hit.id(), hit.source(), hit.score()))
                    .toList();
            TotalHits total = response.hits().total();
            return new ElasticsearchSearchPage<>(total == null ? hits.size() : total.value(), hits);
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not search Elasticsearch documents", exception);
        }
    }

    /** 拒绝空索引名，避免产生难以诊断的下游请求错误。 */
    private static void validateIndexName(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be blank");
        }
    }

    /** 拒绝空文档 ID，保证读写删除操作使用明确的定位键。 */
    private static void validateDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
    }
}
