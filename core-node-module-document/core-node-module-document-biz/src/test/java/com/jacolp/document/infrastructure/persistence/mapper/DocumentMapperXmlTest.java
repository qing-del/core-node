package com.jacolp.document.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DocumentMapperXmlTest {

    @Test
    void visibleDocumentListShouldJoinActiveUserAuthorizations() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("id=\"listActiveVisibleByUserId\"")
                .contains("SELECT d.id, d.owner_user_id, d.title, d.content_object_key")
                .contains("FROM biz_document d")
                .contains("LEFT JOIN biz_document_user du")
                .contains("du.document_id = d.id")
                .contains("du.user_id = #{userId}")
                .contains("du.enabled = 1")
                .contains("du.permission IN ('READ', 'WRITE')")
                .contains("WHERE d.deleted = 0")
                .contains("d.owner_user_id = #{userId}")
                .contains("du.document_id IS NOT NULL")
                .contains("ORDER BY d.last_modify_time DESC, d.id DESC");
    }

    @Test
    void historicalMigrationShouldUseAnAscendingPrimaryKeyCursorWithoutFilteringDeletedRows() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("id=\"selectByIdAfter\"")
                .contains("WHERE id &gt; #{afterId}")
                .contains("ORDER BY id ASC")
                .contains("LIMIT #{limit}");
    }

    @Test
    void adminDocumentTreeQueryShouldOnlyReturnActiveDocumentsInStableTreeOrder() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("id=\"listActiveForAdmin\"")
                .contains("WHERE deleted = 0")
                .contains("ORDER BY owner_user_id ASC, last_modify_time DESC, id DESC");
    }

    @Test
    void batchActiveDocumentLookupShouldRejectDeletedRowsAtTheSqlBoundary() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/document/DocumentMapper.xml")) {
            xml = new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("id=\"selectActiveByIds\"")
                .contains("AND id IN")
                .contains("collection=\"ids\"");
    }
}
