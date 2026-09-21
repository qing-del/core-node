package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Browser-session storage limited to a pending Redis handle and the session identifier that owns
 * it. Full authorization state is intentionally never stored in {@link HttpSession}.
 */
@Component
public final class HttpSessionCoreAgentPendingAuthorizationHandleStore {

    static final String ATTRIBUTE_NAME = HttpSessionCoreAgentPendingAuthorizationHandleStore.class.getName()
            + ".CURRENT_PENDING_HANDLE";
    static final int SESSION_TIMEOUT_SECONDS = 600;

    private final Clock clock;

    public HttpSessionCoreAgentPendingAuthorizationHandleStore() {
        this(Clock.systemUTC());
    }

    HttpSessionCoreAgentPendingAuthorizationHandleStore(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        this.clock = clock;
    }

    /** Replaces the current handle and clamps this browser session's inactivity timeout to ten minutes. */
    public void replace(HttpSession session, IssuedCoreAgentAuthorizationPendingHandle handle) {
        if (session == null || handle == null) {
            throw new IllegalArgumentException("session and handle are required");
        }
        synchronized (session) {
            String sessionId = CoreAgentPendingAuthorizationState.requireSessionId(session.getId());
            Instant now = clock.instant();
            if (!now.isBefore(handle.expiresAt())) {
                throw new IllegalArgumentException("pending handle must be current when stored");
            }
            session.setAttribute(ATTRIBUTE_NAME, new SessionBoundPendingHandle(sessionId, handle.rawHandle()));
            session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        }
    }

    /** Returns the current handle only when the current servlet session retains the original identifier. */
    public Optional<String> find(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        synchronized (session) {
            return current(session);
        }
    }

    /** Removes the handle only if it is still the supplied one; successful Redis conversion can clean up safely. */
    public boolean removeIfMatches(HttpSession session, String expectedRawHandle) {
        if (session == null) {
            return false;
        }
        expectedRawHandle = IssuedCoreAgentAuthorizationPendingHandle.requireRawHandle(expectedRawHandle);
        synchronized (session) {
            Optional<String> current = current(session);
            if (current.isPresent() && current.get().equals(expectedRawHandle)) {
                session.removeAttribute(ATTRIBUTE_NAME);
                return true;
            }
            return false;
        }
    }

    private Optional<String> current(HttpSession session) {
        Object attribute = session.getAttribute(ATTRIBUTE_NAME);
        if (attribute == null) {
            return Optional.empty();
        }
        if (!(attribute instanceof SessionBoundPendingHandle stored)) {
            session.removeAttribute(ATTRIBUTE_NAME);
            throw new IllegalStateException("CORE AGENT pending authorization session attribute is invalid");
        }
        String currentSessionId = CoreAgentPendingAuthorizationState.requireSessionId(session.getId());
        if (!stored.sessionId().equals(currentSessionId)) {
            session.removeAttribute(ATTRIBUTE_NAME);
            return Optional.empty();
        }
        return Optional.of(stored.rawHandle());
    }

    private record SessionBoundPendingHandle(String sessionId, String rawHandle) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private SessionBoundPendingHandle {
            sessionId = CoreAgentPendingAuthorizationState.requireSessionId(sessionId);
            rawHandle = IssuedCoreAgentAuthorizationPendingHandle.requireRawHandle(rawHandle);
        }

        @Override
        public String toString() {
            return "SessionBoundPendingHandle[sessionId=<redacted>, rawHandle=<redacted>]";
        }
    }
}
