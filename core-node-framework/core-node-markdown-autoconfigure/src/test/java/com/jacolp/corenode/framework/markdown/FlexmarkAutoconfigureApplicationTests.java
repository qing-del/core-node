package com.jacolp.corenode.framework.markdown;

import com.jacolp.framework.markdown.MarkdownAutoConfiguration;
import com.jacolp.framework.markdown.MarkdownProperty;
import com.jacolp.framework.markdown.converter.MarkdownHtmlEngine;
import com.jacolp.framework.markdown.converter.MarkdownPublishService;
import com.jacolp.framework.markdown.io.FileStorageService;
import com.jacolp.framework.markdown.io.LocalMarkdownScanner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FlexmarkAutoconfigureApplicationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MarkdownAutoConfiguration.class));

    @Test
    void autoConfigurationProvidesDefaultMarkdownComponents() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MarkdownProperty.class);
            assertThat(context).hasSingleBean(MarkdownHtmlEngine.class);
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context).hasSingleBean(MarkdownPublishService.class);
            assertThat(context).hasSingleBean(LocalMarkdownScanner.class);
        });
    }

}
