package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.api.model.DocumentRoomLifecycleState;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.websocket.exception.DocumentAwarenessException;
import com.jacolp.document.websocket.exception.DocumentRoomAccessException;
import com.jacolp.document.websocket.exception.DocumentRoomLimitExceededException;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class DocumentRoomManagerTest {

    @Test
    void keepsSessionsInSyncingUntilBootstrapCompletesAndStartsPreCloseWhenLastLeaves() {
        DocumentRoomManager manager = new DocumentRoomManager(properties(2));
        DocumentRoom room = manager.getOrCreate(7L, 42L);
        WebSocketSession session = session("session-a");

        DocumentSessionContext context = room.join(session, principal(42L), access(7L, 42L,
                DocumentPermission.WRITE, true), 1001L);

        assertThat(context.syncStatus()).isEqualTo(DocumentSessionSyncStatus.SYNCING);
        assertThat(room.lifecycleState()).isEqualTo(DocumentRoomLifecycleState.ACTIVE);
        room.markActive("session-a");
        assertThat(context.syncStatus()).isEqualTo(DocumentSessionSyncStatus.ACTIVE);

        assertThat(room.leave("session-a")).isTrue();
        assertThat(room.sessionCount()).isZero();
        assertThat(room.lifecycleState()).isEqualTo(DocumentRoomLifecycleState.PRE_CLOSE);
        assertThat(manager.removeIfEmpty(7L)).isTrue();
        assertThat(manager.find(7L)).isEmpty();
        assertThat(manager.removeIfEmpty(7L)).isFalse();
    }

    @Test
    void allowsDifferentAuthorizedUsersAndEnforcesConfiguredRoomSessionLimit() {
        DocumentRoomManager manager = new DocumentRoomManager(properties(1));
        DocumentRoom room = manager.getOrCreate(8L, 42L);

        room.join(session("wrong-scope"), principal(43L), access(8L, 42L,
                DocumentPermission.READ, false), 1002L);

        // The first read-only session consumes the configured slot.
        assertThatThrownBy(() -> room.join(session("second"), principal(42L), access(8L, 42L,
                DocumentPermission.WRITE, true), 1003L))
                .isInstanceOf(DocumentRoomLimitExceededException.class);
        assertThatThrownBy(() -> manager.getOrCreate(8L, 43L))
                .isInstanceOf(DocumentRoomAccessException.class);
    }

    @Test
    void closingRoomAcceptsNewJoinAndReturnsToActiveForReopenRace() {
        DocumentRoom room = new DocumentRoomManager(properties(2)).getOrCreate(9L, 42L);

        assertThat(room.beginClosingIfEmpty()).isTrue();
        room.join(session("returning"), principal(42L), access(9L, 42L,
                DocumentPermission.WRITE, true), 1004L);

        assertThat(room.lifecycleState()).isEqualTo(DocumentRoomLifecycleState.ACTIVE);
        assertThat(room.sessionCount()).isEqualTo(1);
    }

    @Test
    void forwardsRemoteUpdatesToSessionsThatAreStillSynchronizing() throws Exception {
        DocumentRoom room = new DocumentRoomManager(properties(2)).getOrCreate(10L, 42L);
        WebSocketSession synchronizingSession = session("syncing");
        WebSocketSession activeSession = session("active");

        room.join(synchronizingSession, principal(42L), access(10L, 42L,
                DocumentPermission.READ, false), 1005L);
        room.join(activeSession, principal(43L), access(10L, 42L,
                DocumentPermission.WRITE, false), 1006L);
        room.markActive("active");

        WebSocketMessage<?> update = new BinaryMessage(new byte[] {1, 2, 3});
        room.broadcast(update, "active");

        verify(synchronizingSession).sendMessage(update);
    }

    @Test
    void assignsDifferentColorsToMultipleSessionsAndReleasesColorOnLeave() {
        DocumentRoom room = new DocumentRoomManager(properties(3)).getOrCreate(11L, 42L);
        DocumentSessionContext first = room.join(session("session-a"), principal(42L),
                access(11L, 42L, DocumentPermission.WRITE, true), 1007L);
        DocumentSessionContext second = room.join(session("session-b"), principal(42L),
                access(11L, 42L, DocumentPermission.WRITE, true), 1008L);
        DocumentSessionContext otherUser = room.join(session("session-c"), principal(43L),
                access(11L, 42L, DocumentPermission.READ, false), 1009L);

        assertThat(first.username()).isEqualTo("user");
        assertThat(first.cursorColor()).matches("#[0-9A-F]{6}");
        assertThat(second.cursorColor()).isNotEqualTo(first.cursorColor());
        assertThat(otherUser.cursorColor()).isNotEqualTo(first.cursorColor());
        assertThat(otherUser.cursorColor()).isNotEqualTo(second.cursorColor());

        assertThat(room.leave("session-a")).isTrue();
        DocumentSessionContext replacement = room.join(session("session-a"), principal(42L),
                access(11L, 42L, DocumentPermission.WRITE, true), 1010L);
        assertThat(replacement.cursorColor()).isEqualTo(first.cursorColor());
    }

    @Test
    void replacesLatestAwarenessFrameAndClearsItWhenSessionLeaves() {
        DocumentRoom room = new DocumentRoomManager(properties(2)).getOrCreate(18L, 42L);
        DocumentSessionContext context = room.join(session("awareness-cache"), principal(42L),
                access(18L, 42L, DocumentPermission.WRITE, true), 1801L);
        DocumentWsBinaryFrame first = new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                UUID.randomUUID(), new byte[] {1, 2});
        DocumentWsBinaryFrame latest = new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                UUID.randomUUID(), new byte[] {3, 4, 5});

        room.rememberAwareness("awareness-cache", first);
        room.rememberAwareness("awareness-cache", latest);

        DocumentWsBinaryFrame cached = context.latestAwareness().orElseThrow();
        assertThat(cached.eventId()).isEqualTo(latest.eventId());
        assertThat(cached.payload()).containsExactly(3, 4, 5);
        assertThat(room.awarenessSnapshots()).singleElement()
                .satisfies(snapshot -> assertThat(snapshot.latestFrame().eventId()).isEqualTo(latest.eventId()));

        DocumentSessionContext removed = room.removeSession("awareness-cache").orElseThrow();
        assertThat(removed.latestAwareness()).isEmpty();
        assertThat(room.awarenessSnapshots()).isEmpty();
        assertThatThrownBy(() -> room.rememberAwareness("awareness-cache", latest))
                .isInstanceOf(DocumentRoomAccessException.class);
    }

    @Test
    void rejoiningTheSameSessionKeepsItsColorAndDoesNotConsumeAnotherSlot() {
        DocumentRoom room = new DocumentRoomManager(properties(1)).getOrCreate(12L, 42L);
        WebSocketSession session = session("session-a");

        DocumentSessionContext first = room.join(session, principal(42L),
                access(12L, 42L, DocumentPermission.WRITE, true), 1011L);
        DocumentSessionContext repeated = room.join(session, principal(42L),
                access(12L, 42L, DocumentPermission.WRITE, true), 1011L);

        assertThat(repeated).isSameAs(first);
        assertThat(repeated.cursorColor()).isEqualTo(first.cursorColor());
        assertThat(room.sessionCount()).isEqualTo(1);
    }

    @Test
    void rejectsRoomJoinWithoutAwarenessClientId() {
        DocumentRoom room = new DocumentRoomManager(properties(1)).getOrCreate(16L, 42L);

        assertThatThrownBy(() -> room.join(session("missing-awareness"), principal(42L),
                access(16L, 42L, DocumentPermission.WRITE, true)))
                .isInstanceOf(DocumentAwarenessException.class)
                .hasMessageContaining("awarenessClientId");
        assertThat(room.sessionCount()).isZero();
    }

    @Test
    void rejectsAwarenessClientIdMismatchAndRoomConflict() {
        DocumentRoom room = new DocumentRoomManager(properties(3)).getOrCreate(17L, 42L);
        WebSocketSession first = session("first-awareness");

        room.join(first, principal(42L), access(17L, 42L, DocumentPermission.WRITE, true), 1701L);

        assertThatThrownBy(() -> room.join(first, principal(42L),
                access(17L, 42L, DocumentPermission.WRITE, true), 1702L))
                .isInstanceOf(DocumentAwarenessException.class)
                .hasMessageContaining("another awarenessClientId");
        assertThatThrownBy(() -> room.join(session("second-awareness"), principal(43L),
                access(17L, 42L, DocumentPermission.READ, false), 1701L))
                .isInstanceOf(DocumentAwarenessException.class)
                .hasMessageContaining("already active");
        assertThat(room.sessionCount()).isEqualTo(1);
    }

    @Test
    void roomCapacityFailureDoesNotConsumeCursorColor() {
        DocumentRoom room = new DocumentRoom(15L, 42L, properties(1),
                new DocumentCursorColorAllocator(List.of("#112233")));

        room.join(session("first"), principal(42L), access(15L, 42L, DocumentPermission.WRITE, true), 1012L);
        assertThatThrownBy(() -> room.join(session("second"), principal(43L),
                access(15L, 42L, DocumentPermission.READ, false), 1013L))
                .isInstanceOf(DocumentRoomLimitExceededException.class);

        assertThat(room.leave("first")).isTrue();
        DocumentSessionContext replacement = room.join(session("second"), principal(43L),
                access(15L, 42L, DocumentPermission.READ, false), 1013L);
        assertThat(replacement.cursorColor()).isEqualTo("#112233");
    }

    @Test
    void linearlyProbesRoomColorsAndRejectsPaletteExhaustionWithoutLeakingAColor() {
        DocumentProperties properties = properties(3);
        DocumentRoom room = new DocumentRoom(13L, 42L, properties,
                new DocumentCursorColorAllocator(List.of("#112233", "#445566")));

        DocumentSessionContext first = room.join(session("Aa"), principal(42L),
                access(13L, 42L, DocumentPermission.WRITE, true), 1014L);
        DocumentSessionContext second = room.join(session("BB"), principal(43L),
                access(13L, 42L, DocumentPermission.READ, false), 1015L);

        assertThat(first.cursorColor()).isEqualTo("#112233");
        assertThat(second.cursorColor()).isEqualTo("#445566");
        assertThatThrownBy(() -> room.join(session("third"), principal(42L),
                access(13L, 42L, DocumentPermission.WRITE, true), 1016L))
                .isInstanceOf(DocumentRoomLimitExceededException.class);
        assertThat(room.sessionCount()).isEqualTo(2);
    }

    @Test
    void sendFailureRemovesSessionAndMakesItsColorAvailableAgain() throws Exception {
        DocumentRoom room = new DocumentRoomManager(properties(3)).getOrCreate(14L, 42L);
        WebSocketSession sender = session("sender");
        WebSocketSession failing = session("failing");
        doThrow(new java.io.IOException("send failed")).when(failing).sendMessage(org.mockito.ArgumentMatchers.any());

        room.join(sender, principal(42L), access(14L, 42L, DocumentPermission.WRITE, true), 1017L);
        DocumentSessionContext failedContext = room.join(failing, principal(43L),
                access(14L, 42L, DocumentPermission.READ, false), 1018L);
        String releasedColor = failedContext.cursorColor();

        room.broadcast(new BinaryMessage(new byte[] {1, 2, 3}), "sender");

        assertThat(room.sessionCount()).isEqualTo(1);
        DocumentSessionContext replacement = room.join(session("failing"), principal(43L),
                access(14L, 42L, DocumentPermission.READ, false), 1018L);
        assertThat(replacement.cursorColor()).isEqualTo(releasedColor);
    }

    private static DocumentProperties properties(int maxRoomSessions) {
        DocumentProperties properties = new DocumentProperties();
        properties.getWebsocket().setMaxRoomSessions(maxRoomSessions);
        properties.getWebsocket().setMaxSendQueueBytes(1024);
        return properties;
    }

    private static CurrentPrincipal principal(long userId) {
        return new CurrentPrincipal(userId, "user", "user", "password", List.of("USER"), List.of("document:write"));
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static DocumentAccess access(long documentId, long ownerUserId, DocumentPermission permission,
                                         boolean owner) {
        LocalDateTime now = LocalDateTime.now();
        DocumentDO document = new DocumentDO(documentId, ownerUserId, "title", null, 0L, now,
                ownerUserId, false, 0L, now, now);
        return new DocumentAccess(document, permission, owner);
    }
}
