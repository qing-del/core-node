package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.jacolp.document.websocket.exception.DocumentRoomLimitExceededException;
import org.junit.jupiter.api.Test;

class DocumentCursorColorAllocatorTest {

    @Test
    void allocatesAStableUniqueColorForEachActiveSession() {
        DocumentCursorColorAllocator allocator = new DocumentCursorColorAllocator(List.of(
                "#112233", "#445566", "#778899"));

        String first = allocator.allocate("session-a");
        String repeated = allocator.allocate("session-a");
        String second = allocator.allocate("session-b");

        assertThat(repeated).isEqualTo(first);
        assertThat(second).isNotEqualTo(first);
        assertThat(allocator.size()).isEqualTo(2);
    }

    @Test
    void linearlyProbesWhenSessionHashesSelectTheSameColor() {
        DocumentCursorColorAllocator allocator = new DocumentCursorColorAllocator(List.of("#112233", "#445566"));

        // "Aa" 与 "BB" 的 Java hashCode 相同，能够稳定覆盖同一起始色碰撞。
        String first = allocator.allocate("Aa");
        String second = allocator.allocate("BB");

        assertThat(first).isEqualTo("#112233");
        assertThat(second).isEqualTo("#445566");
    }

    @Test
    void releasingAColorMakesItAvailableAgainAndIsIdempotent() {
        DocumentCursorColorAllocator allocator = new DocumentCursorColorAllocator(List.of("#112233"));

        String first = allocator.allocate("session-a");
        allocator.release("session-a");
        allocator.release("session-a");
        String replacement = allocator.allocate("session-b");

        assertThat(replacement).isEqualTo(first);
        assertThat(allocator.size()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidSessionIdsAndExhaustedPalettes() {
        DocumentCursorColorAllocator allocator = new DocumentCursorColorAllocator(List.of("#112233"));

        assertThatThrownBy(() -> allocator.allocate(" "))
                .isInstanceOf(IllegalArgumentException.class);
        allocator.allocate("session-a");
        assertThatThrownBy(() -> allocator.allocate("session-b"))
                .isInstanceOf(DocumentRoomLimitExceededException.class);
    }

    @Test
    void normalizesHexColorsAndRejectsDuplicatePaletteEntries() {
        DocumentCursorColorAllocator allocator = new DocumentCursorColorAllocator(List.of("#aBcDeF"));

        assertThat(allocator.allocate("session-a")).isEqualTo("#ABCDEF");
        assertThatThrownBy(() -> new DocumentCursorColorAllocator(List.of("#112233", "#112233")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
