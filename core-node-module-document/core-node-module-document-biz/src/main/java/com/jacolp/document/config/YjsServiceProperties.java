package com.jacolp.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 内部无状态 Yjs 服务的连接配置。 */
@ConfigurationProperties(prefix = "jacolp.yjs-service")
public class YjsServiceProperties {

    private String baseUrl;

    /** 返回 Yjs 服务的基础地址。 */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** 设置 Yjs 服务的基础地址。 */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** 返回非空基础地址；文档模块启用但未配置时立即失败。 */
    public String requireBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("jacolp.yjs-service.base-url is required when document is enabled");
        }
        return baseUrl;
    }
}
