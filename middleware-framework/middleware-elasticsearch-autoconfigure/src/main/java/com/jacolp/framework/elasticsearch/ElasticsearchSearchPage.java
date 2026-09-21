package com.jacolp.framework.elasticsearch;

import java.util.List;

/** Immutable page of source-backed Elasticsearch hits. */
public record ElasticsearchSearchPage<T>(long total, List<ElasticsearchSearchHit<T>> hits) {

    /** 固化命中列表，防止调用方在查询返回后修改结果页内容。 */
    public ElasticsearchSearchPage {
        hits = List.copyOf(hits);
    }
}
