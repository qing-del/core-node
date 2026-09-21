package com.jacolp.framework.elasticsearch;

/** Outcome returned by a common Elasticsearch index operation. */
public record ElasticsearchWriteResult(String documentId, long version, String result) {
}
