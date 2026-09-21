package com.jacolp.framework.elasticsearch;

/** Runtime exception used by the module-neutral Elasticsearch operations API. */
public class ElasticsearchOperationException extends RuntimeException {

    /** 创建不带底层原因的 Elasticsearch 操作异常。 */
    public ElasticsearchOperationException(String message) {
        super(message);
    }

    /** 保留 SDK 或网络层异常作为根因，便于上层记录和诊断。 */
    public ElasticsearchOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
