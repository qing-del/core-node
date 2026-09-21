package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentSessionPresenceRegistryTest {

    @Test
    void registersRenewsAndRemovesLeaseForLocalSession() {
        DocumentRedisRepository repository = mock(DocumentRedisRepository.class);
        DocumentProperties properties = new DocumentProperties();
        properties.setCloseDelayMs(30_000L);
        properties.setSessionPresenceRefreshMs(10_000L);
        DocumentSessionPresenceRegistry registry = new DocumentSessionPresenceRegistry(repository, properties);

        registry.register(7L, "session-a");
        registry.refreshLocalLeases();
        registry.unregister("session-a");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(2)).savePresence(keys.capture(), eq(60_000L));
        assertThat(keys.getAllValues()).allMatch(key -> key.matches("document:presence:7:[0-9a-f-]{36}:session-a"));
        verify(repository).deletePresence(keys.getValue());
    }
}
