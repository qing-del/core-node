package com.jacolp.document.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DocumentOpLogMapperXmlTest {

    @Test
    void insertBatchUsesNonAutoIncrementNoOpForDuplicateKeys() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentOpLogMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("<insert id=\"insertBatchIgnoringDuplicates\">")
                .contains("INSERT INTO document_op_log")
                .contains("(document_id, redis_op_id, client_update_id, update_data, operator_id, operator_type, create_time)")
                .contains("ON DUPLICATE KEY UPDATE document_id = document_id")
                .doesNotContain("ON DUPLICATE KEY UPDATE id = id");
    }
}
