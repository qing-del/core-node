package com.jacolp.document.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class DocumentPendingUpdateTest {

    @Test
    void shouldDefensivelyCopyBinaryUpdate() {
        byte[] source = {1, 2, 3};
        DocumentPendingUpdate update = new DocumentPendingUpdate(
                12L, source, "3fa85f64-5717-4562-b3fc-2c963f66afa6", 7L, "USER", 10L);

        source[0] = 9;
        byte[] read = update.updateData();
        read[1] = 8;

        assertThat(update.updateData()).containsExactly(1, 2, 3);
    }

    @Test
    void shouldRejectMissingClientUpdateId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DocumentPendingUpdate(
                12L, new byte[]{1}, "not-a-uuid", 7L, "USER", 10L));
    }
}
