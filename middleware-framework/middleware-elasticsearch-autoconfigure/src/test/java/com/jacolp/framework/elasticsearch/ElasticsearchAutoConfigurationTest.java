package com.jacolp.framework.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ElasticsearchAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElasticsearchAutoConfiguration.class));

    @Test
    void createsOfficialClientAndBindsLogicalIndexes() {
        contextRunner.withPropertyValues(
                "jacolp.elasticsearch.uris=http://localhost:9200",
                "jacolp.elasticsearch.index.document=middleware-document")
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(ElasticsearchClient.class);
                    assertThat(context).hasSingleBean(ElasticsearchIndexResolver.class);
                    assertThat(context).hasSingleBean(ElasticsearchOperations.class);
                    assertThat(context.getBean(ElasticsearchProperties.class).getIndex())
                            .containsEntry("document", "middleware-document");
                    assertThat(context.getBean(ElasticsearchIndexResolver.class).requireIndex("document"))
                            .isEqualTo("middleware-document");
                });
    }
}
