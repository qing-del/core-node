# middleware-elasticsearch-autoconfigure

为项目模块提供统一的 Elasticsearch 自动配置、逻辑索引解析和常用文档操作 API。
业务模块通常只需引入 `middleware-elasticsearch-starter`，不需要自行创建 REST Transport、处理基础认证或重复封装单文档 CRUD。

```xml
<dependency>
    <groupId>com.jacolp</groupId>
    <artifactId>middleware-elasticsearch-starter</artifactId>
</dependency>
```

```yaml
jacolp:
  elasticsearch:
    uris: http://localhost:9200
    username:
    password:
    index:
      document: middleware-document
```

```java
@Service
class DocumentProjectionService {
    private final ElasticsearchIndexResolver indexes;
    private final ElasticsearchOperations operations;

    DocumentProjectionService(ElasticsearchIndexResolver indexes, ElasticsearchOperations operations) {
        this.indexes = indexes;
        this.operations = operations;
    }

    void save(String id, DocumentSearchEntity document) {
        operations.index(indexes.requireIndex("document"), id, document);
    }
}
```

`ElasticsearchOperations` 提供 index 是否存在、创建、单文档 index/get/delete 与传入官方 `Query` 的分页查询。
Mapping、Analyzer、Alias、Bulk、Aggregation 和业务查询策略不属于通用层；这些高级场景仍可注入自动配置的官方
`ElasticsearchClient`，避免通用 API 固化任何模块的索引设计。
