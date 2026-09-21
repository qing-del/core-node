package com.jacolp.framework.elasticsearch;

/** One source-backed hit returned by {@link ElasticsearchOperations#search}. */
public record ElasticsearchSearchHit<T>(String documentId, T source, Double score) {
}
