package com.jacolp.document.application.fileindex;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.CurrentPrincipalAccessor;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;
import com.jacolp.common.security.oauth2.authorization.PermissionScopeMatcher;
import com.jacolp.framework.elasticsearch.ElasticsearchIndexResolver;
import com.jacolp.framework.elasticsearch.ElasticsearchOperationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 按当前 JWT 身份和 scope 从 file 索引查询补全结果。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "enabled", havingValue = "true")
public class FileIndexCompletionService {

    private static final String FILE_NAME_KEYWORD = "fileName.keyword";
    private static final String RESOURCE_TYPE = "resourceType";
    private static final String RESOURCE_ID = "resourceId";
    private static final String RESOURCE_URL = "resourceUrl";
    private static final String VISIBLE_USER_IDS = "visibleUserIds";
    private static final String IS_PUBLIC = "isPublic";
    private static final String IS_DELETE = "isDelete";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchIndexResolver indexResolver;
    private final CurrentPrincipalAccessor principalAccessor;

    /** 生产路径从 Spring Security 上下文读取已验证 JWT，不接受客户端提交 userId。 */
    @Autowired
    public FileIndexCompletionService(ElasticsearchClient elasticsearchClient,
                                      ElasticsearchIndexResolver indexResolver) {
        this(elasticsearchClient, indexResolver, new SecurityContextCurrentPrincipalAccessor());
    }

    /** 为单元测试注入可控的当前主体读取器。 */
    FileIndexCompletionService(ElasticsearchClient elasticsearchClient, ElasticsearchIndexResolver indexResolver,
                               CurrentPrincipalAccessor principalAccessor) {
        this.elasticsearchClient = Objects.requireNonNull(elasticsearchClient,
                "elasticsearchClient must not be null");
        this.indexResolver = Objects.requireNonNull(indexResolver, "indexResolver must not be null");
        this.principalAccessor = Objects.requireNonNull(principalAccessor, "principalAccessor must not be null");
    }

    /** 执行当前用户可见的文件名前缀查询；limit 缺省为 10，最大为 20。 */
    public List<FileCompletionItem> complete(String keyword, Integer limit) {
        String normalizedKeyword = keyword == null ? "" : keyword;
        int normalizedLimit = normalizeLimit(limit);
        CurrentPrincipal principal = principalAccessor.currentPrincipal()
                .orElseThrow(() -> new AuthenticationException("当前登录信息已失效"));
        Set<FileIndexResourceType> allowedTypes = allowedTypes(principal);
        if (allowedTypes.isEmpty()) {
            // 没有任何 file 资源读取 scope 时直接返回，避免向 ES 发出无意义的宽查询。
            return List.of();
        }

        String indexName = indexResolver.requireIndex(FileIndexMappingInitializer.LOGICAL_INDEX_NAME);
        Query query = buildQuery(normalizedKeyword, principal.userId(), allowedTypes);
        try {
            SearchResponse<FileCompletionItem> response = elasticsearchClient.search(request -> request
                            .index(indexName)
                            .query(query)
                            .source(source -> source.filter(filter -> filter.includes(
                                    "fileName", RESOURCE_TYPE, RESOURCE_ID, RESOURCE_URL)))
                            .sort(sort -> sort.field(field -> field.field(FILE_NAME_KEYWORD).order(SortOrder.Asc)))
                            .sort(sort -> sort.field(field -> field.field(RESOURCE_TYPE).order(SortOrder.Asc)))
                            .sort(sort -> sort.field(field -> field.field(RESOURCE_ID).order(SortOrder.Asc)))
                            .size(normalizedLimit), FileCompletionItem.class);
            if (response == null || response.hits() == null || response.hits().hits() == null) {
                return List.of();
            }
            return response.hits().hits().stream().map(hit -> hit.source()).filter(Objects::nonNull)
                    .map(FileIndexCompletionService::sanitize).toList();
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not search file completion index", exception);
        }
    }

    /** 使用固定的三类 read scope 计算当前 token 可查询的资源类型。 */
    static Set<FileIndexResourceType> allowedTypes(CurrentPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        EnumSet<FileIndexResourceType> allowed = EnumSet.noneOf(FileIndexResourceType.class);
        if (PermissionScopeMatcher.grants(principal.scopes(), "note:read")) {
            allowed.add(FileIndexResourceType.NOTE);
        }
        if (PermissionScopeMatcher.grants(principal.scopes(), "media:read")) {
            allowed.add(FileIndexResourceType.IMAGE);
        }
        if (PermissionScopeMatcher.grants(principal.scopes(), "document:read")) {
            allowed.add(FileIndexResourceType.DOCUMENT);
        }
        return Set.copyOf(allowed);
    }

    /** 构造删除过滤、公开/用户可见过滤和资源类型 scope 过滤。 */
    static Query buildQuery(String keyword, long userId, Set<FileIndexResourceType> allowedTypes) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(allowedTypes, "allowedTypes must not be null");
        if (allowedTypes.isEmpty()) {
            throw new IllegalArgumentException("allowedTypes must not be empty");
        }

        List<Query> typeQueries = allowedTypes.stream().sorted()
                .map(type -> term(RESOURCE_TYPE, type.name())).toList();
        Query visibility = new Query.Builder().bool(bool -> bool
                .should(term(IS_PUBLIC, true))
                .should(term(VISIBLE_USER_IDS, String.valueOf(userId)))
                .minimumShouldMatch("1")).build();
        Query types = new Query.Builder().bool(bool -> bool
                .should(typeQueries)
                .minimumShouldMatch("1")).build();
        List<Query> filters = new ArrayList<>();
        filters.add(new Query.Builder().prefix(prefix -> prefix.field(FILE_NAME_KEYWORD)
                .value(keyword == null ? "" : keyword)).build());
        filters.add(term(IS_DELETE, false));
        filters.add(visibility);
        filters.add(types);
        return new Query.Builder().bool(bool -> bool.filter(filters)).build();
    }

    private static Query term(String field, String value) {
        return new Query.Builder().term(term -> term.field(field).value(value)).build();
    }

    private static Query term(String field, boolean value) {
        return new Query.Builder().term(term -> term.field(field).value(value)).build();
    }

    /** 响应层再次保证 resourceUrl 只对 IMAGE 暴露，即使索引中存在异常旧数据也不泄露。 */
    private static FileCompletionItem sanitize(FileCompletionItem item) {
        return item.resourceType() == FileIndexResourceType.IMAGE
                ? item
                : new FileCompletionItem(item.fileName(), item.resourceType(), item.resourceId(), null);
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return value;
    }
}
