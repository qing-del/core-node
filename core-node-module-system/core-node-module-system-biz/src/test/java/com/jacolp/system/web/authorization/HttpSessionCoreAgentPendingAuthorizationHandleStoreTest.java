package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpSessionCoreAgentPendingAuthorizationHandleStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final String HANDLE = opaque((byte) 5);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StoreConfiguration.class);

    @Test
    void registersTheSessionHandleStore() {
        runner.run(context -> assertThat(context.getBeansOfType(HttpSessionCoreAgentPendingAuthorizationHandleStore.class))
                .hasSize(1));
    }

    @Test
    void retainsOnlyOneOpaqueHandleAndSessionBindingAndSetsTenMinuteTimeout() {
        HttpSessionCoreAgentPendingAuthorizationHandleStore store = new HttpSessionCoreAgentPendingAuthorizationHandleStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpSession session = new MockHttpSession();
        store.replace(session, handle());

        Object attribute = session.getAttribute(HttpSessionCoreAgentPendingAuthorizationHandleStore.ATTRIBUTE_NAME);
        assertThat(attribute).isNotNull();
        assertThat(attribute.toString()).doesNotContain(HANDLE);
        assertThat(session.getMaxInactiveInterval())
                .isEqualTo(HttpSessionCoreAgentPendingAuthorizationHandleStore.SESSION_TIMEOUT_SECONDS);
        assertThat(store.find(session)).contains(HANDLE);
        assertThat(store.removeIfMatches(session, opaque((byte) 6))).isFalse();
        assertThat(store.removeIfMatches(session, HANDLE)).isTrue();
        assertThat(store.find(session)).isEmpty();
    }

    @Test
    void rejectsExpiredHandlesAndFailsClosedForSessionChangesOrPollution() {
        HttpSessionCoreAgentPendingAuthorizationHandleStore store = new HttpSessionCoreAgentPendingAuthorizationHandleStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpSession expiredSession = new MockHttpSession();
        assertThatIllegalArgumentException().isThrownBy(() -> store.replace(expiredSession,
                new IssuedCoreAgentAuthorizationPendingHandle(HANDLE, NOW)));

        AtomicReference<String> id = new AtomicReference<>("first-session");
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        HttpSession changing = session(id, attributes);
        store.replace(changing, handle());
        id.set("rotated-session");
        assertThat(store.find(changing)).isEmpty();

        HttpSession polluted = mock(HttpSession.class);
        when(polluted.getId()).thenReturn("session-id");
        when(polluted.getAttribute(HttpSessionCoreAgentPendingAuthorizationHandleStore.ATTRIBUTE_NAME)).thenReturn("bad");
        assertThatIllegalStateException().isThrownBy(() -> store.find(polluted));
        verify(polluted).removeAttribute(HttpSessionCoreAgentPendingAuthorizationHandleStore.ATTRIBUTE_NAME);
    }

    private static HttpSession session(AtomicReference<String> id, Map<String, Object> attributes) {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenAnswer(invocation -> id.get());
        when(session.getAttribute(HttpSessionCoreAgentPendingAuthorizationHandleStore.ATTRIBUTE_NAME))
                .thenAnswer(invocation -> attributes.get(HttpSessionCoreAgentPendingAuthorizationHandleStore.ATTRIBUTE_NAME));
        doAnswer(invocation -> {
            attributes.put((String) invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(session).setAttribute(eq(HttpSessionCoreAgentPendingAuthorizationHandleStore.ATTRIBUTE_NAME),
                org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            attributes.remove((String) invocation.getArgument(0));
            return null;
        }).when(session).removeAttribute(HttpSessionCoreAgentPendingAuthorizationHandleStore.ATTRIBUTE_NAME);
        return session;
    }

    private static IssuedCoreAgentAuthorizationPendingHandle handle() {
        return new IssuedCoreAgentAuthorizationPendingHandle(HANDLE, NOW.plus(Duration.ofMinutes(10)));
    }

    private static String opaque(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(HttpSessionCoreAgentPendingAuthorizationHandleStore.class)
    static class StoreConfiguration {
    }
}
