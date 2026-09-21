package com.jacolp.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class DocumentMetadataTest {
    @Test
    void trimsAndPreservesSafeMetadata() {
        DocumentMetadata metadata = new DocumentMetadata(1L, 2L, "  Design  ", 3L, 4L, false);

        assertThat(metadata.title()).isEqualTo("Design");
    }

    @Test
    void rejectsInvalidDocumentIdentifiersAndTitles() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DocumentMetadata(0L, 2L, "Design", 0L, null, false));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DocumentMetadata(1L, 2L, "  ", 0L, null, false));
    }
}
