package com.jacolp.framework.elasticsearch;

/** Resolves a module-neutral logical index name from {@code jacolp.elasticsearch.index.*}. */
public interface ElasticsearchIndexResolver {

    /**
     * Returns the configured physical index name for a logical name.
     *
     * @throws ElasticsearchOperationException if the logical index name is blank or has not been configured
     */
    String requireIndex(String logicalIndexName);
}
