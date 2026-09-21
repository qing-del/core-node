package com.jacolp.document.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DocumentBindingMapperXmlTest {

    @Test
    void resourceNodeMapperShouldCreateAndRetargetNodesWithoutTargetUniqueness() throws IOException {
        String xml = read("mapper/document/ResourceNodeMapper.xml");

        assertThat(xml).contains("INSERT INTO biz_resource_node")
                .contains("useGeneratedKeys=\"true\"")
                .contains("UPDATE biz_resource_node")
                .contains("resource_type = #{resourceType}")
                .contains("target_id = #{targetId}")
                .doesNotContain("UNIQUE");
    }

    @Test
    void documentRelationMapperShouldSoftDeleteByCompositeIdentity() throws IOException {
        String xml = read("mapper/document/DocumentRelationMapper.xml");

        assertThat(xml).contains("source_document_id")
                .contains("ref_id")
                .contains("id=\"selectBySourceDocumentIdAndRefId\"")
                .contains("id=\"cloneActiveBySourceDocumentIdAndRefId\"")
                .contains("id=\"activateBySourceDocumentIdAndRefId\"")
                .contains("id=\"softDeleteBySourceDocumentIdAndRefId\"")
                .contains("SET is_delete = 1")
                .doesNotContain("FOREIGN KEY");
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = DocumentBindingMapperXmlTest.class.getClassLoader().getResourceAsStream(resource)) {
            return new String(Objects.requireNonNull(stream, "mapper XML must be on the test classpath").readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
