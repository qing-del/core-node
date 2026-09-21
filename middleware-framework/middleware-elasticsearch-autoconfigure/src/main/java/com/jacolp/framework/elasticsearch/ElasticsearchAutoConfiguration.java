package com.jacolp.framework.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Provides a shared official Elasticsearch Java API client without business index behavior. */
@AutoConfiguration
@ConditionalOnClass({ElasticsearchClient.class, RestClient.class})
@ConditionalOnProperty(prefix = "jacolp.elasticsearch", name = "uris")
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchAutoConfiguration {
    /** 创建共享 REST 客户端，并在凭据成对配置时启用基本认证。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient elasticsearchRestClient(ElasticsearchProperties properties) {
        List<String> uris = properties.getUris();
        if (uris.isEmpty() || uris.stream().anyMatch(uri -> uri == null || uri.isBlank())) {
            throw new IllegalStateException("jacolp.elasticsearch.uris must contain at least one non-blank URI");
        }
        if (properties.hasPartialCredentials()) {
            throw new IllegalStateException("jacolp.elasticsearch.username and jacolp.elasticsearch.password must be configured together");
        }
        // 只有显式提供完整凭据时才配置认证，允许本地无认证集群继续使用同一套自动配置。
        RestClientBuilder builder = RestClient.builder(uris.stream().map(HttpHost::create).toArray(HttpHost[]::new));
        if (properties.hasCompleteCredentials()) {
            CredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(client -> client.setDefaultCredentialsProvider(credentials));
        }
        return builder.build();
    }

    /** 将 Apache REST 客户端包装为 Elasticsearch Java API transport。 */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchTransport elasticsearchTransport(RestClient elasticsearchRestClient) {
        return new RestClientTransport(elasticsearchRestClient, new JacksonJsonpMapper());
    }

    /** 暴露官方 Elasticsearch Java API 客户端供业务模块处理高级请求。 */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport elasticsearchTransport) {
        return new ElasticsearchClient(elasticsearchTransport);
    }

    /** 暴露逻辑索引名解析器，避免业务模块直接读取连接配置。 */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchIndexResolver elasticsearchIndexResolver(ElasticsearchProperties properties) {
        return new DefaultElasticsearchIndexResolver(properties);
    }

    /** 暴露覆盖常用索引、读写和查询操作的通用门面。 */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchOperations elasticsearchOperations(ElasticsearchClient elasticsearchClient) {
        return new DefaultElasticsearchOperations(elasticsearchClient);
    }
}
