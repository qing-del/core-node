package com.jacolp.document.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DocumentUserMappingMapperXmlTest {

    @Test
    void authorizationMapperKeepsCompositeKeyAndOwnerGuards() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentUserMappingMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("id=\"selectByDocumentId\"")
                .contains("id=\"selectEnabledReadableByDocumentIds\"")
                .contains("permission IN ('READ', 'WRITE')")
                .contains("document_id IN")
                .contains("id=\"selectByDocumentIdAndUserId\"")
                .contains("id=\"selectEnabledByDocumentIdAndUserId\"")
                .contains("id=\"upsertByDocumentOwner\"")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("deleted = 0")
                .contains("id=\"disableByDocumentOwner\"")
                .contains("SET enabled = 0")
                .doesNotContain("DELETE FROM biz_document_user");
    }
}
