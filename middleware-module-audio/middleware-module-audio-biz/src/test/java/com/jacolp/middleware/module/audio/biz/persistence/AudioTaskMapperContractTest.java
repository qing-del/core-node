package com.jacolp.middleware.module.audio.biz.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AudioTaskMapperContractTest {

    @Test
    void retryPreparationResetsStateAndCallbacksCompareAttempt() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "mapper/audio/AudioTaskMapper.xml");
        String mapperXml;
        try (InputStream input = resource.getInputStream()) {
            mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(mapperXml)
                .contains("<update id=\"prepareRetry\">")
                .contains("SET status = 0,")
                .contains("retry_time = retry_time + 1")
                .contains("AND retry_time = #{attempt}");
        assertThat(mapperXml).doesNotContain("<update id=\"incrementRetryTime\">");
    }
}
