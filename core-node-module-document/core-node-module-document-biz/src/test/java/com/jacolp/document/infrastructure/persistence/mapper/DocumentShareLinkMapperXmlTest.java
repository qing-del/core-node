package com.jacolp.document.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DocumentShareLinkMapperXmlTest {

    @Test
    void shareLinkMapperShouldPersistHashedTokensAndGuardQuota() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentShareLinkMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("id=\"selectByTokenHash\"")
                .contains("token_hash = #{tokenHash}")
                .contains("id=\"selectByIdForUpdate\"")
                .contains("FOR UPDATE")
                .contains("id=\"incrementUsedCountIfAvailable\"")
                .contains("used_count < max_uses")
                .contains("id=\"insertRedemption\"")
                .contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("SELECT token");
    }

    @Test
    void shareLinkMapperShouldKeepRevocationAsSoftState() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentShareLinkMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("id=\"revokeByIdAndCreator\"")
                .contains("SET enabled = 0")
                .doesNotContain("DELETE FROM biz_document_share_link");
    }
}
