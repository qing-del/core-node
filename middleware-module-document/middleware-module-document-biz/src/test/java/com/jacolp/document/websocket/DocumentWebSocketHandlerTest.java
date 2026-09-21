package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessDeniedException;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.application.close.DocumentRoomLifecycleService;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentLinkAcceptedRedisOperations;
import com.jacolp.document.infrastructure.redis.DocumentPendingBinding;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import com.jacolp.document.websocket.exception.DocumentBootstrapException;
import com.jacolp.document.websocket.protocol.DocumentWsAwarenessAction;
import com.jacolp.document.websocket.protocol.DocumentWsAwarenessMeta;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsControlMessage;
import com.jacolp.document.websocket.protocol.DocumentWsControlType;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import com.jacolp.document.websocket.protocol.DocumentBindingCommandType;
import com.jacolp.document.websocket.protocol.DocumentBindingEnvelope;
import com.jacolp.document.websocket.protocol.DocumentBindingTargetType;
import com.jacolp.document.websocket.protocol.DocumentLinkCodec;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class DocumentWebSocketHandlerTest {

    @Test
    void ownerCanJoinAndSubmitUpdatesAfterWriteAclIsRechecked() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-a", principal(42L, "document:write"));
        DocumentDO document = document(7L, 42L);
        DocumentAccess access = access(document, DocumentPermission.WRITE, true);

        when(accessService.requireRead(7L, 42L)).thenReturn(access);
        when(accessService.requireWrite(7L, 42L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        when(redisRepository.appendPendingUpdate(any(DocumentPendingUpdate.class))).thenReturn("123-0");
        when(documentMapper.updateLastModificationIfActive(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L)))
                .thenReturn(1);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        UUID joinRequestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, joinRequestId, 7L, null, null, null, null, 101L)));

        ArgumentCaptor<WebSocketMessage<?>> joinMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(2)).sendMessage(joinMessages.capture());
        assertThat(codec.decodeControl((TextMessage) joinMessages.getAllValues().get(0)).type())
                .isEqualTo(DocumentWsControlType.JOIN_ACCEPTED);
        assertThat(codec.decodeControl((TextMessage) joinMessages.getAllValues().get(1)).type())
                .isEqualTo(DocumentWsControlType.SYNC_COMPLETE);
        verify(bootstrapService).sendBootstrap(eq(7L), any(WebSocketSession.class));
        verify(presenceRegistry).register(7L, "session-a");
        verify(lifecycleService).reopen(document, 42L);

        UUID updateId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        byte[] update = new byte[] {0, -1, 1};
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, updateId, update)));

        ArgumentCaptor<DocumentPendingUpdate> pendingUpdate = ArgumentCaptor.forClass(DocumentPendingUpdate.class);
        verify(redisRepository).appendPendingUpdate(pendingUpdate.capture());
        assertThat(pendingUpdate.getValue().documentId()).isEqualTo(7L);
        assertThat(pendingUpdate.getValue().clientUpdateId()).isEqualTo(updateId.toString());
        assertThat(pendingUpdate.getValue().updateData()).containsExactly(update);
        verify(accessService).requireWrite(7L, 42L);

        ArgumentCaptor<WebSocketMessage<?>> updateAck = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(updateAck.capture());
        DocumentWsControlMessage ack = codec.decodeControl((TextMessage) updateAck.getAllValues().getLast());
        assertThat(ack.type()).isEqualTo(DocumentWsControlType.UPDATE_ACCEPTED);
        assertThat(ack.requestId()).isEqualTo(updateId);
        assertThat(ack.clientUpdateId()).isEqualTo(updateId);
        assertThat(ack.redisOpId()).isEqualTo("123-0");
        verify(schedulePublisher).scheduleFlushLog(7L);
    }

    @Test
    void acceptsLinkAfterAtomicEnqueueAndBroadcastsOnlyRawYjsUpdate() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);
        when(accessService.requireWrite(7L, 42L)).thenReturn(ownerAccess);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        when(redisRepository.appendLinkPendingUpdate(any(), any()))
                .thenReturn(new DocumentLinkAcceptedRedisOperations("200-0", "201-0"));
        when(documentMapper.updateLastModificationIfActive(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L)))
                .thenReturn(1);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        WebSocketSession owner = session("session-link-owner", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-link-collaborator", principal(43L, "document:read"));
        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 3101L)));
        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 3102L)));

        UUID linkId = UUID.randomUUID();
        byte[] rawYjsUpdate = new byte[] {0, -1, 1, 127};
        DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(1, DocumentBindingCommandType.BIND,
                UUID.randomUUID(), DocumentBindingTargetType.DOCUMENT, 99L);
        handler.handleMessage(owner, codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.LINK, linkId,
                DocumentLinkCodec.encode(envelope, rawYjsUpdate))));

        ArgumentCaptor<DocumentPendingUpdate> pendingUpdate = ArgumentCaptor.forClass(DocumentPendingUpdate.class);
        ArgumentCaptor<DocumentPendingBinding> pendingBinding = ArgumentCaptor.forClass(DocumentPendingBinding.class);
        verify(redisRepository).appendLinkPendingUpdate(pendingUpdate.capture(), pendingBinding.capture());
        assertThat(pendingUpdate.getValue().updateData()).containsExactly(rawYjsUpdate);
        assertThat(pendingUpdate.getValue().clientUpdateId()).isEqualTo(linkId.toString());
        assertThat(pendingBinding.getValue().documentId()).isEqualTo(7L);
        assertThat(pendingBinding.getValue().envelopeData()).containsExactly(envelope.encodeBody());

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(owner, times(4)).sendMessage(ownerMessages.capture());
        String acceptedJson = ((TextMessage) ownerMessages.getAllValues().getLast()).getPayload();
        assertThat(acceptedJson).contains("\"type\":\"LINK_ACCEPTED\"")
                .contains("\"documentId\":7")
                .contains("\"clientUpdateId\":\"" + linkId + "\"")
                .contains("\"updatesRedisOpId\":\"200-0\"")
                .contains("\"bindingRedisOpId\":\"201-0\"")
                .contains("\"status\":\"QUEUED\"");

        ArgumentCaptor<WebSocketMessage<?>> collaboratorMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(4)).sendMessage(collaboratorMessages.capture());
        DocumentWsBinaryFrame broadcast = codec.decodeBinary(
                (BinaryMessage) collaboratorMessages.getAllValues().getLast());
        assertThat(broadcast.type()).isEqualTo(DocumentWsFrameType.CRDT_UPDATE);
        assertThat(broadcast.eventId()).isEqualTo(linkId);
        assertThat(broadcast.payload()).containsExactly(rawYjsUpdate);
        verify(schedulePublisher).scheduleFlushLog(7L);
    }

    @Test
    void schedulesLinkFlushWhenAcceptedAckCannotBeDelivered() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);
        when(accessService.requireWrite(7L, 42L)).thenReturn(ownerAccess);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        when(redisRepository.appendLinkPendingUpdate(any(), any()))
                .thenReturn(new DocumentLinkAcceptedRedisOperations("200-0", "201-0"));
        when(documentMapper.updateLastModificationIfActive(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L)))
                .thenReturn(1);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        WebSocketSession owner = session("session-link-ack-failure-owner", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-link-ack-failure-collaborator", principal(43L, "document:read"));
        doAnswer(invocation -> {
            WebSocketMessage<?> outgoing = invocation.getArgument(0);
            if (outgoing instanceof TextMessage text && text.getPayload().contains("\"type\":\"LINK_ACCEPTED\"")) {
                throw new IOException("connection closed while acknowledging LINK");
            }
            return null;
        }).when(owner).sendMessage(any(WebSocketMessage.class));

        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 3201L)));
        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 3202L)));
        DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(1, DocumentBindingCommandType.BIND,
                UUID.randomUUID(), DocumentBindingTargetType.DOCUMENT, 99L);
        handler.handleMessage(owner, codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.LINK,
                UUID.randomUUID(), DocumentLinkCodec.encode(envelope, new byte[] {1, 2, 3}))));

        verify(redisRepository).appendLinkPendingUpdate(any(DocumentPendingUpdate.class), any(DocumentPendingBinding.class));
        verify(schedulePublisher).scheduleFlushLog(7L);
        ArgumentCaptor<WebSocketMessage<?>> collaboratorMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(4)).sendMessage(collaboratorMessages.capture());
        assertThat(collaboratorMessages.getAllValues()).noneMatch(BinaryMessage.class::isInstance);
    }

    @Test
    void linkRedisFailureDoesNotAckOrBroadcast() throws Exception {
        WebSocketTestFixture fixture = websocketFixture(128);
        joinOwnerAndCollaborator(fixture);
        UUID linkId = UUID.randomUUID();
        DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(1, DocumentBindingCommandType.UNBIND,
                UUID.randomUUID(), DocumentBindingTargetType.DOCUMENT, 99L);
        when(fixture.redisRepository().appendLinkPendingUpdate(any(), any()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.LINK, linkId, DocumentLinkCodec.encode(envelope, new byte[] {1, 2, 3}))));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(fixture.owner(), times(4)).sendMessage(ownerMessages.capture());
        DocumentWsControlMessage error = fixture.codec().decodeControl(
                (TextMessage) ownerMessages.getAllValues().getLast());
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.code()).isEqualTo("DOCUMENT_UPDATE_ACCEPT_FAILED");
        verify(fixture.collaborator(), times(3)).sendMessage(any(WebSocketMessage.class));
        verify(fixture.documentMapper(), never()).updateLastModificationIfActive(anyLong(), anyLong(), any(), anyLong());
        verify(fixture.schedulePublisher(), never()).scheduleFlushLog(anyLong());
    }

    @Test
    void malformedLinkEnvelopeIsRejectedAsProtocolError() throws Exception {
        WebSocketTestFixture fixture = websocketFixture(128);
        joinOwner(fixture);
        UUID linkId = UUID.randomUUID();

        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.LINK, linkId, new byte[] {0, 0, 0, 27, 1})));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(fixture.owner(), times(3)).sendMessage(ownerMessages.capture());
        DocumentWsControlMessage error = fixture.codec().decodeControl(
                (TextMessage) ownerMessages.getAllValues().getLast());
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.requestId()).isEqualTo(linkId);
        assertThat(error.code()).isEqualTo("DOCUMENT_PROTOCOL_ERROR");
        verify(fixture.redisRepository(), never()).appendLinkPendingUpdate(any(), any());
    }

    @Test
    void readOnlyCollaboratorCanBootstrapButCannotSubmitClientUpdate() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-read", principal(43L, "document:read"));
        DocumentAccess access = access(document(7L, 42L), DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 43L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 102L)));

        UUID updateId = UUID.randomUUID();
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, updateId, new byte[] {1, 2, 3})));

        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(messages.capture());
        DocumentWsControlMessage error = codec.decodeControl((TextMessage) messages.getAllValues().getLast());
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.code()).isEqualTo("DOCUMENT_FORBIDDEN");
        verify(redisRepository, never()).appendPendingUpdate(any(DocumentPendingUpdate.class));
        verify(documentMapper, never()).updateLastModificationIfActive(anyLong(), anyLong(), any(), anyLong());
        verify(accessService, never()).requireWrite(anyLong(), anyLong());
    }

    @Test
    void writeCollaboratorCanJoinAndSubmitUpdatesInTheOwnerRoom() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-write", principal(43L, "document:write"));
        DocumentDO document = document(7L, 42L);
        DocumentAccess access = access(document, DocumentPermission.WRITE, false);
        when(accessService.requireRead(7L, 43L)).thenReturn(access);
        when(accessService.requireWrite(7L, 43L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        when(redisRepository.appendPendingUpdate(any(DocumentPendingUpdate.class))).thenReturn("124-0");
        when(documentMapper.updateLastModificationIfActive(eq(7L), eq(43L), any(LocalDateTime.class), eq(43L)))
                .thenReturn(1);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 103L)));
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, UUID.randomUUID(), new byte[] {4, 5})));

        verify(redisRepository).appendPendingUpdate(any(DocumentPendingUpdate.class));
        verify(documentMapper).updateLastModificationIfActive(eq(7L), eq(43L), any(LocalDateTime.class), eq(43L));
        verify(schedulePublisher).scheduleFlushLog(7L);
    }

    @Test
    void writeAclWithoutGlobalWriteScopeCannotSubmitClientUpdate() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-read-scope", principal(43L, "document:read"));
        DocumentAccess access = access(document(7L, 42L), DocumentPermission.WRITE, false);
        when(accessService.requireRead(7L, 43L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 104L)));
        UUID updateId = UUID.randomUUID();
        handler.handleMessage(session, codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, updateId, new byte[] {6, 7})));

        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(messages.capture());
        DocumentWsControlMessage error = codec.decodeControl((TextMessage) messages.getAllValues().getLast());
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.code()).isEqualTo("DOCUMENT_FORBIDDEN");
        verify(accessService, never()).requireWrite(anyLong(), anyLong());
        verify(redisRepository, never()).appendPendingUpdate(any(DocumentPendingUpdate.class));
        verify(documentMapper, never()).updateLastModificationIfActive(anyLong(), anyLong(), any(), anyLong());
        verify(schedulePublisher, never()).scheduleFlushLog(anyLong());
    }

    @Test
    void rejectsUnauthorizedJoinWithoutCreatingRoomOrPresence() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-denied", principal(43L, "document:read"));
        when(accessService.requireRead(7L, 43L)).thenThrow(DocumentAccessDeniedException.forbidden());

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 105L)));

        ArgumentCaptor<WebSocketMessage<?>> message = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(codec.decodeControl((TextMessage) message.getValue()).code()).isEqualTo("DOCUMENT_FORBIDDEN");
        verify(bootstrapService, never()).sendBootstrap(anyLong(), any());
        verify(presenceRegistry, never()).register(anyLong(), any());
    }

    @Test
    void rejectsJoiningAnotherDocumentOnTheSameWebSocketSession() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        WebSocketSession session = session("session-b", principal(42L, "document:write"));
        when(accessService.requireRead(7L, 42L)).thenReturn(access(document(7L, 42L), DocumentPermission.WRITE, true));
        when(accessService.requireRead(8L, 42L)).thenReturn(access(document(8L, 42L), DocumentPermission.WRITE, true));
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties);

        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 106L)));
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 8L, null, null, null, null, 107L)));

        ArgumentCaptor<WebSocketMessage<?>> error = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(3)).sendMessage(error.capture());
        DocumentWsControlMessage response = codec.decodeControl((TextMessage) error.getAllValues().getLast());
        assertThat(response.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(response.code()).isEqualTo("DOCUMENT_FORBIDDEN");
    }

    @Test
    void bootstrapFailureReleasesTheSessionColor() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        WebSocketSession session = session("session-bootstrap", principal(42L, "document:write"));
        DocumentAccess access = access(document(7L, 42L), DocumentPermission.WRITE, true);
        when(accessService.requireRead(7L, 42L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DocumentBootstrapException("bootstrap failed"))
                .when(bootstrapService).sendBootstrap(eq(7L), any(WebSocketSession.class));

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        handler.handleMessage(session, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 108L)));

        DocumentRoom room = roomManager.find(7L).orElseThrow();
        assertThat(room.sessionCount()).isZero();
        DocumentSessionContext replacement = room.join(session("session-bootstrap", principal(42L, "document:write")),
                principal(42L, "document:write"), access, 108L);
        assertThat(replacement.cursorColor()).matches("#[0-9A-F]{6}");
        verify(presenceRegistry, atLeastOnce()).unregister("session-bootstrap");
    }

    @Test
    void normalCloseAndTransportErrorReleaseSessionColors() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess access = access(document, DocumentPermission.WRITE, true);
        when(accessService.requireRead(7L, 42L)).thenReturn(access);
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());
        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);

        WebSocketSession normalSession = session("session-normal-close", principal(42L, "document:write"));
        handler.handleMessage(normalSession, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 109L)));
        DocumentRoom room = roomManager.find(7L).orElseThrow();
        String normalColor = room.requireSession("session-normal-close").cursorColor();
        handler.afterConnectionClosed(normalSession, CloseStatus.NORMAL);
        assertThat(room.sessionCount()).isZero();
        assertThat(room.join(session("session-normal-close", principal(42L, "document:write")),
                principal(42L, "document:write"), access, 109L).cursorColor())
                .isEqualTo(normalColor);
        room.leave("session-normal-close");

        WebSocketSession transportSession = session("session-transport-error", principal(42L, "document:write"));
        handler.handleMessage(transportSession, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 110L)));
        String transportColor = room.requireSession("session-transport-error").cursorColor();
        handler.handleTransportError(transportSession, new IOException("transport failed"));
        assertThat(room.sessionCount()).isZero();
        assertThat(room.join(session("session-transport-error", principal(42L, "document:write")),
                principal(42L, "document:write"), access, 110L).cursorColor())
                .isEqualTo(transportColor);
    }

    @Test
    void rejectsMissingAndInvalidAwarenessClientIdBeforeCreatingRoom() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);

        WebSocketSession missing = session("session-awareness-missing", principal(42L, "document:write"));
        handler.handleMessage(missing, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null)));
        ArgumentCaptor<WebSocketMessage<?>> missingMessage = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(missing).sendMessage(missingMessage.capture());
        assertThat(codec.decodeControl((TextMessage) missingMessage.getValue()).code())
                .isEqualTo("DOCUMENT_AWARENESS_CLIENT_ID_REQUIRED");

        WebSocketSession invalid = session("session-awareness-invalid", principal(42L, "document:write"));
        handler.handleMessage(invalid, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, 0L)));
        ArgumentCaptor<WebSocketMessage<?>> invalidMessage = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(invalid).sendMessage(invalidMessage.capture());
        assertThat(codec.decodeControl((TextMessage) invalidMessage.getValue()).code())
                .isEqualTo("DOCUMENT_AWARENESS_CLIENT_ID_INVALID");

        WebSocketSession negative = session("session-awareness-negative", principal(42L, "document:write"));
        handler.handleMessage(negative, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null, -1L)));
        ArgumentCaptor<WebSocketMessage<?>> negativeMessage = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(negative).sendMessage(negativeMessage.capture());
        assertThat(codec.decodeControl((TextMessage) negativeMessage.getValue()).code())
                .isEqualTo("DOCUMENT_AWARENESS_CLIENT_ID_INVALID");

        assertThat(roomManager.find(7L)).isEmpty();
        verify(accessService, never()).requireRead(anyLong(), anyLong());
        verify(presenceRegistry, never()).register(anyLong(), any());
    }

    @Test
    void publishesAwarenessMetadataOnJoinAndRemoveOnLeave() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        WebSocketSession owner = session("session-awareness-owner", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-awareness-collaborator", principal(43L, "document:read"));

        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 2001L)));
        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 2002L)));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(owner, times(3)).sendMessage(ownerMessages.capture());
        DocumentWsAwarenessMeta ownerUpsert = codec.decodeAwarenessMeta(
                (TextMessage) ownerMessages.getAllValues().get(2));
        assertThat(ownerUpsert.action()).isEqualTo(DocumentWsAwarenessAction.UPSERT);
        assertThat(ownerUpsert.documentId()).isEqualTo(7L);
        assertThat(ownerUpsert.awarenessClientId()).isEqualTo(2002L);
        assertThat(ownerUpsert.sessionId()).isEqualTo("session-awareness-collaborator");
        assertThat(ownerUpsert.userId()).isEqualTo(43L);
        assertThat(ownerUpsert.name()).isEqualTo("alice");
        assertThat(ownerUpsert.color()).matches("#[0-9A-F]{6}");

        ArgumentCaptor<WebSocketMessage<?>> collaboratorMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(3)).sendMessage(collaboratorMessages.capture());
        DocumentWsAwarenessMeta collaboratorUpsert = codec.decodeAwarenessMeta(
                (TextMessage) collaboratorMessages.getAllValues().get(1));
        assertThat(collaboratorUpsert.action()).isEqualTo(DocumentWsAwarenessAction.UPSERT);
        assertThat(collaboratorUpsert.awarenessClientId()).isEqualTo(2001L);
        assertThat(collaboratorUpsert.sessionId()).isEqualTo("session-awareness-owner");
        assertThat(collaboratorUpsert.userId()).isEqualTo(42L);

        handler.handleMessage(owner, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.LEAVE_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null)));
        ArgumentCaptor<WebSocketMessage<?>> collaboratorAfterLeave = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(4)).sendMessage(collaboratorAfterLeave.capture());
        DocumentWsAwarenessMeta remove = codec.decodeAwarenessMeta(
                (TextMessage) collaboratorAfterLeave.getAllValues().get(3));
        assertThat(remove.action()).isEqualTo(DocumentWsAwarenessAction.REMOVE);
        assertThat(remove.documentId()).isEqualTo(7L);
        assertThat(remove.awarenessClientId()).isEqualTo(2001L);
        assertThat(remove.sessionId()).isEqualTo("session-awareness-owner");
        assertThat(remove.userId()).isNull();
        assertThat(remove.name()).isNull();
        assertThat(remove.color()).isNull();

        handler.afterConnectionClosed(owner, CloseStatus.NORMAL);
        verify(collaborator, times(4)).sendMessage(any(WebSocketMessage.class));
        assertThat(roomManager.find(7L).orElseThrow().sessionCount()).isEqualTo(1);
        verify(presenceRegistry).unregister("session-awareness-owner");
    }

    @Test
    void rejectsOversizedAwarenessPayloadBeforeCachingOrBroadcasting() throws Exception {
        WebSocketTestFixture fixture = websocketFixture(4);
        joinOwnerAndCollaborator(fixture);
        UUID eventId = UUID.randomUUID();

        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.AWARENESS, eventId, new byte[] {1, 2, 3, 4, 5})));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(fixture.owner(), times(4)).sendMessage(ownerMessages.capture());
        DocumentWsControlMessage error = fixture.codec().decodeControl(
                (TextMessage) ownerMessages.getAllValues().get(3));
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.requestId()).isEqualTo(eventId);
        assertThat(error.code()).isEqualTo("DOCUMENT_UPDATE_TOO_LARGE");
        assertThat(error.message()).isEqualTo("Awareness payload exceeds configured maximum size");

        verify(fixture.collaborator(), times(3)).sendMessage(any(WebSocketMessage.class));
        assertThat(fixture.roomManager().find(7L).orElseThrow().awarenessSnapshots())
                .allSatisfy(snapshot -> assertThat(snapshot.latestFrame()).isNull());
    }

    @Test
    void rejectsEmptyAwarenessPayloadBeforeCachingOrBroadcasting() throws Exception {
        WebSocketTestFixture fixture = websocketFixture(4);
        joinOwnerAndCollaborator(fixture);
        UUID eventId = UUID.randomUUID();

        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.AWARENESS, eventId, new byte[0])));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(fixture.owner(), times(4)).sendMessage(ownerMessages.capture());
        DocumentWsControlMessage error = fixture.codec().decodeControl(
                (TextMessage) ownerMessages.getAllValues().get(3));
        assertThat(error.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(error.requestId()).isEqualTo(eventId);
        assertThat(error.code()).isEqualTo("DOCUMENT_PROTOCOL_ERROR");
        assertThat(error.message()).isEqualTo("Awareness payload must not be empty");

        verify(fixture.collaborator(), times(3)).sendMessage(any(WebSocketMessage.class));
        assertThat(fixture.roomManager().find(7L).orElseThrow().awarenessSnapshots())
                .allSatisfy(snapshot -> assertThat(snapshot.latestFrame()).isNull());
    }

    @Test
    void acceptsAwarenessPayloadAtConfiguredMaximumSize() throws Exception {
        WebSocketTestFixture fixture = websocketFixture(4);
        joinOwnerAndCollaborator(fixture);
        UUID eventId = UUID.randomUUID();
        byte[] payload = new byte[] {1, 2, 3, 4};

        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.AWARENESS, eventId, payload)));

        ArgumentCaptor<WebSocketMessage<?>> collaboratorMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(fixture.collaborator(), times(4)).sendMessage(collaboratorMessages.capture());
        DocumentWsBinaryFrame forwarded = fixture.codec().decodeBinary(
                (BinaryMessage) collaboratorMessages.getAllValues().get(3));
        assertThat(forwarded.eventId()).isEqualTo(eventId);
        assertThat(forwarded.payload()).containsExactly(payload);

        DocumentWsBinaryFrame cached = fixture.roomManager().find(7L).orElseThrow().awarenessSnapshots().stream()
                .filter(snapshot -> snapshot.context().sessionId().equals(fixture.owner().getId()))
                .findFirst()
                .orElseThrow()
                .latestFrame();
        assertThat(cached).isNotNull();
        assertThat(cached.eventId()).isEqualTo(eventId);
        assertThat(cached.payload()).containsExactly(payload);
    }

    @Test
    void rejectsEmptyAndOversizedClientUpdatesThroughSharedPayloadValidation() throws Exception {
        WebSocketTestFixture fixture = websocketFixture(3);
        joinOwner(fixture);
        UUID emptyEventId = UUID.randomUUID();
        UUID oversizedEventId = UUID.randomUUID();

        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, emptyEventId, new byte[0])));
        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CLIENT_UPDATE, oversizedEventId, new byte[] {1, 2, 3, 4})));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(fixture.owner(), times(4)).sendMessage(ownerMessages.capture());
        DocumentWsControlMessage emptyError = fixture.codec().decodeControl(
                (TextMessage) ownerMessages.getAllValues().get(2));
        assertThat(emptyError.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(emptyError.requestId()).isEqualTo(emptyEventId);
        assertThat(emptyError.code()).isEqualTo("DOCUMENT_PROTOCOL_ERROR");
        assertThat(emptyError.message()).isEqualTo("Yjs update must not be empty");

        DocumentWsControlMessage oversizedError = fixture.codec().decodeControl(
                (TextMessage) ownerMessages.getAllValues().get(3));
        assertThat(oversizedError.type()).isEqualTo(DocumentWsControlType.ERROR);
        assertThat(oversizedError.requestId()).isEqualTo(oversizedEventId);
        assertThat(oversizedError.code()).isEqualTo("DOCUMENT_UPDATE_TOO_LARGE");
        assertThat(oversizedError.message()).isEqualTo("Yjs update exceeds configured maximum size");
        verify(fixture.redisRepository(), never()).appendPendingUpdate(any(DocumentPendingUpdate.class));
    }

    @Test
    void replaysOnlyLatestAwarenessFrameToNewSession() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        WebSocketSession owner = session("session-awareness-replay-owner", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-awareness-replay-new", principal(43L, "document:read"));
        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 2201L)));

        UUID firstEventId = UUID.randomUUID();
        UUID latestEventId = UUID.randomUUID();
        handler.handleMessage(owner, codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                firstEventId, new byte[] {1, 1})));
        handler.handleMessage(owner, codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                latestEventId, new byte[] {2, 3, 5, 8})));

        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 2202L)));

        ArgumentCaptor<WebSocketMessage<?>> replayedMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(4)).sendMessage(replayedMessages.capture());
        assertThat(codec.decodeControl((TextMessage) replayedMessages.getAllValues().get(0)).type())
                .isEqualTo(DocumentWsControlType.JOIN_ACCEPTED);
        DocumentWsAwarenessMeta ownerMeta = codec.decodeAwarenessMeta(
                (TextMessage) replayedMessages.getAllValues().get(1));
        assertThat(ownerMeta.action()).isEqualTo(DocumentWsAwarenessAction.UPSERT);
        assertThat(ownerMeta.sessionId()).isEqualTo("session-awareness-replay-owner");
        DocumentWsBinaryFrame replayed = codec.decodeBinary((BinaryMessage) replayedMessages.getAllValues().get(2));
        assertThat(replayed.type()).isEqualTo(DocumentWsFrameType.AWARENESS);
        assertThat(replayed.eventId()).isEqualTo(latestEventId);
        assertThat(replayed.payload()).containsExactly(2, 3, 5, 8);
        assertThat(codec.decodeControl((TextMessage) replayedMessages.getAllValues().get(3)).type())
                .isEqualTo(DocumentWsControlType.SYNC_COMPLETE);

        UUID forwardedEventId = UUID.randomUUID();
        handler.handleMessage(owner, codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                forwardedEventId, new byte[] {13, 21})));
        ArgumentCaptor<WebSocketMessage<?>> forwardedMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(5)).sendMessage(forwardedMessages.capture());
        DocumentWsBinaryFrame forwarded = codec.decodeBinary((BinaryMessage) forwardedMessages.getAllValues().get(4));
        assertThat(forwarded.eventId()).isEqualTo(forwardedEventId);
        assertThat(forwarded.payload()).containsExactly(13, 21);

        handler.handleMessage(owner, codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.LEAVE_DOCUMENT, UUID.randomUUID(), 7L, null, null, null, null)));
        ArgumentCaptor<WebSocketMessage<?>> removedMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(6)).sendMessage(removedMessages.capture());
        DocumentWsAwarenessMeta remove = codec.decodeAwarenessMeta(
                (TextMessage) removedMessages.getAllValues().get(5));
        assertThat(remove.action()).isEqualTo(DocumentWsAwarenessAction.REMOVE);
        assertThat(remove.awarenessClientId()).isEqualTo(2201L);
        verify(presenceRegistry).unregister("session-awareness-replay-owner");
    }

    @Test
    void awarenessReplaySendFailureRemovesNewSessionAndPublishesRemove() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        WebSocketSession owner = session("session-awareness-replay-failure-owner", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-awareness-replay-failure-new", principal(43L, "document:read"));
        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 2301L)));
        handler.handleMessage(owner, codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                UUID.randomUUID(), new byte[] {9, 7, 4})));

        AtomicInteger collaboratorSendCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (collaboratorSendCount.incrementAndGet() == 3) {
                throw new IOException("replay send failed");
            }
            return null;
        }).when(collaborator).sendMessage(any(WebSocketMessage.class));

        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 2302L)));

        ArgumentCaptor<WebSocketMessage<?>> ownerMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(owner, times(3)).sendMessage(ownerMessages.capture());
        DocumentWsAwarenessMeta remove = codec.decodeAwarenessMeta(
                (TextMessage) ownerMessages.getAllValues().get(2));
        assertThat(remove.action()).isEqualTo(DocumentWsAwarenessAction.REMOVE);
        assertThat(remove.awarenessClientId()).isEqualTo(2302L);
        assertThat(remove.sessionId()).isEqualTo("session-awareness-replay-failure-new");
        assertThat(roomManager.find(7L).orElseThrow().sessionCount()).isEqualTo(1);
        verify(presenceRegistry).unregister("session-awareness-replay-failure-new");
    }

    @Test
    void removesFailedBroadcastSessionAndPublishesItsAwarenessRemove() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        DocumentAccess ownerAccess = access(document, DocumentPermission.WRITE, true);
        DocumentAccess collaboratorAccess = access(document, DocumentPermission.READ, false);
        when(accessService.requireRead(7L, 42L)).thenReturn(ownerAccess);
        when(accessService.requireRead(7L, 43L)).thenReturn(collaboratorAccess);

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        WebSocketSession owner = session("session-awareness-failed", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-awareness-survivor", principal(43L, "document:read"));
        handler.handleMessage(owner, codec.encodeControl(joinControl(7L, 2101L)));
        doThrow(new IOException("send failed")).when(owner).sendMessage(any(WebSocketMessage.class));

        handler.handleMessage(collaborator, codec.encodeControl(joinControl(7L, 2102L)));

        ArgumentCaptor<WebSocketMessage<?>> collaboratorMessages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(collaborator, times(4)).sendMessage(collaboratorMessages.capture());
        DocumentWsAwarenessMeta remove = codec.decodeAwarenessMeta(
                (TextMessage) collaboratorMessages.getAllValues().get(2));
        assertThat(remove.action()).isEqualTo(DocumentWsAwarenessAction.REMOVE);
        assertThat(remove.awarenessClientId()).isEqualTo(2101L);
        assertThat(remove.sessionId()).isEqualTo("session-awareness-failed");
        assertThat(roomManager.find(7L).orElseThrow().sessionCount()).isEqualTo(1);
        verify(presenceRegistry).unregister("session-awareness-failed");
    }

    private static WebSocketTestFixture websocketFixture(int maxUpdateBytes) {
        DocumentProperties properties = new DocumentProperties();
        properties.getWebsocket().setMaxUpdateBytes(maxUpdateBytes);
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentBootstrapService bootstrapService = mock(DocumentBootstrapService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        DocumentSessionPresenceRegistry presenceRegistry = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomLifecycleService lifecycleService = mock(DocumentRoomLifecycleService.class);
        DocumentRoomManager roomManager = new DocumentRoomManager(properties);
        DocumentDO document = document(7L, 42L);
        when(accessService.requireRead(7L, 42L)).thenReturn(access(document, DocumentPermission.WRITE, true));
        when(accessService.requireRead(7L, 43L)).thenReturn(access(document, DocumentPermission.READ, false));
        when(redisRepository.findRoomMeta(7L)).thenReturn(Optional.empty());

        DocumentWebSocketHandler handler = handler(codec, documentMapper, accessService, redisRepository,
                bootstrapService, schedulePublisher, presenceRegistry, lifecycleService, properties, roomManager);
        WebSocketSession owner = session("session-awareness-validation-owner", principal(42L, "document:write"));
        WebSocketSession collaborator = session("session-awareness-validation-collaborator",
                principal(43L, "document:read"));
        return new WebSocketTestFixture(handler, codec, roomManager, redisRepository, documentMapper,
                schedulePublisher, owner, collaborator);
    }

    private static void joinOwner(WebSocketTestFixture fixture) throws Exception {
        fixture.handler().handleMessage(fixture.owner(), fixture.codec().encodeControl(joinControl(7L, 2401L)));
    }

    private static void joinOwnerAndCollaborator(WebSocketTestFixture fixture) throws Exception {
        joinOwner(fixture);
        fixture.handler().handleMessage(fixture.collaborator(), fixture.codec().encodeControl(joinControl(7L, 2402L)));
    }

    private static DocumentWebSocketHandler handler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                                    DocumentAccessService accessService,
                                                    DocumentRedisRepository redisRepository,
                                                    DocumentBootstrapService bootstrapService,
                                                    DocumentSchedulePublisher schedulePublisher,
                                                    DocumentSessionPresenceRegistry presenceRegistry,
                                                    DocumentRoomLifecycleService lifecycleService,
                                                    DocumentProperties properties) {
        return handler(codec, documentMapper, accessService, redisRepository, bootstrapService, schedulePublisher,
                presenceRegistry, lifecycleService, properties, new DocumentRoomManager(properties));
    }

    private static DocumentWebSocketHandler handler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                                    DocumentAccessService accessService,
                                                    DocumentRedisRepository redisRepository,
                                                    DocumentBootstrapService bootstrapService,
                                                    DocumentSchedulePublisher schedulePublisher,
                                                    DocumentSessionPresenceRegistry presenceRegistry,
                                                    DocumentRoomLifecycleService lifecycleService,
                                                    DocumentProperties properties,
                                                    DocumentRoomManager roomManager) {
        return new DocumentWebSocketHandler(codec, documentMapper, accessService, redisRepository,
                roomManager, bootstrapService, schedulePublisher, presenceRegistry,
                lifecycleService, properties);
    }

    private static WebSocketSession session(String sessionId, CurrentPrincipal principal) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(DocumentWebSocketHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal));
        return session;
    }

    private static CurrentPrincipal principal(long userId, String scope) {
        return new CurrentPrincipal(userId, "alice", "user", "password", List.of("USER"), List.of(scope));
    }

    private static DocumentWsControlMessage joinControl(long documentId, long awarenessClientId) {
        return new DocumentWsControlMessage(1, DocumentWsControlType.JOIN_DOCUMENT, UUID.randomUUID(), documentId,
                null, null, null, null, awarenessClientId);
    }

    private static DocumentDO document(long id, long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, ownerUserId, "title", null, 0L, now, ownerUserId, false, 0L, now, now);
    }

    private static DocumentAccess access(DocumentDO document, DocumentPermission permission, boolean owner) {
        return new DocumentAccess(document, permission, owner);
    }

    private record WebSocketTestFixture(DocumentWebSocketHandler handler, DocumentWsCodec codec,
                                        DocumentRoomManager roomManager, DocumentRedisRepository redisRepository,
                                        DocumentMapper documentMapper, DocumentSchedulePublisher schedulePublisher,
                                        WebSocketSession owner, WebSocketSession collaborator) {
    }
}
