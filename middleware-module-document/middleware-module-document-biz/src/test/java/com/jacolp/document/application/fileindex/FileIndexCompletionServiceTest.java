package com.jacolp.document.application.fileindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.CurrentPrincipalAccessor;
import com.jacolp.framework.elasticsearch.ElasticsearchIndexResolver;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileIndexCompletionServiceTest {

    private ElasticsearchClient elasticsearchClient;
    private ElasticsearchIndexResolver indexResolver;
    private CurrentPrincipalAccessor principalAccessor;
    private FileIndexCompletionService service;

    @BeforeEach
    void setUp() {
        elasticsearchClient = mock(ElasticsearchClient.class);
        indexResolver = mock(ElasticsearchIndexResolver.class);
        principalAccessor = mock(CurrentPrincipalAccessor.class);
        when(indexResolver.requireIndex(FileIndexMappingInitializer.LOGICAL_INDEX_NAME)).thenReturn("file-v07");
        service = new FileIndexCompletionService(elasticsearchClient, indexResolver, principalAccessor);
    }

    @Test
    void searchesWithPrefixVisibilityScopeTypeFiltersAndStableSorts() throws Exception {
        when(principalAccessor.currentPrincipal()).thenReturn(Optional.of(principal(42L,
                "note:read", "document:read")));
        SearchResponse<FileCompletionItem> response = SearchResponse.of(search -> search.took(1).timedOut(false)
                .shards(shards -> shards.total(1).successful(1).skipped(0).failed(0))
                .hits(hits -> hits
                .total(total -> total.relation(TotalHitsRelation.Eq).value(2))
                .hits(Hit.of(hit -> hit.index("file-v07").id("DOCUMENT:2").source(
                                new FileCompletionItem("设计文档", FileIndexResourceType.DOCUMENT, "2", "bad-url"))),
                        Hit.of(hit -> hit.index("file-v07").id("NOTE:1").source(
                                new FileCompletionItem("设计笔记", FileIndexResourceType.NOTE, "1", null))))));
        SearchRequest[] captured = new SearchRequest[1];
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<SearchRequest.Builder, ?> builder = invocation.getArgument(0);
            captured[0] = ((co.elastic.clients.util.ObjectBuilder<SearchRequest>) builder
                    .apply(new SearchRequest.Builder())).build();
            return response;
        }).when(elasticsearchClient).search(any(Function.class), eq(FileCompletionItem.class));

        List<FileCompletionItem> result = service.complete("设计", 10);

        assertThat(result).containsExactly(
                new FileCompletionItem("设计文档", FileIndexResourceType.DOCUMENT, "2", null),
                new FileCompletionItem("设计笔记", FileIndexResourceType.NOTE, "1", null));
        assertThat(captured[0].index()).containsExactly("file-v07");
        assertThat(captured[0].size()).isEqualTo(10);
        assertThat(captured[0].source().filter().includes()).containsExactly(
                "fileName", "resourceType", "resourceId", "resourceUrl");
        assertThat(captured[0].sort()).extracting(sort -> sort.field().field()).containsExactly(
                "fileName.keyword", "resourceType", "resourceId");

        Query query = captured[0].query();
        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().filter()).hasSize(4);
        assertThat(query.bool().filter().getFirst().prefix().field()).isEqualTo("fileName.keyword");
        assertThat(query.bool().filter().getFirst().prefix().value()).isEqualTo("设计");
        assertThat(query.bool().filter().get(1).term().field()).isEqualTo("isDelete");
        assertThat(query.bool().filter().get(1).term().value().booleanValue()).isFalse();
        assertThat(query.bool().filter().get(2).bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(query.bool().filter().get(2).bool().should()).hasSize(2);
        assertThat(query.bool().filter().get(3).bool().should()).hasSize(2);
    }

    @Test
    void returnsEmptyWithoutQueryWhenTokenHasNoFileReadScope() {
        when(principalAccessor.currentPrincipal()).thenReturn(Optional.of(principal(42L, "account:read")));

        assertThat(service.complete("anything", null)).isEmpty();
        verifyNoInteractions(elasticsearchClient);
    }

    @Test
    void rejectsMissingAuthenticationAndOutOfRangeLimit() {
        when(principalAccessor.currentPrincipal()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete("anything", 10)).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.complete("anything", 21)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 20");
    }

    private static CurrentPrincipal principal(long userId, String... scopes) {
        return new CurrentPrincipal(userId, "alice", "user", "password", List.of("USER"), List.of(scopes));
    }
}
