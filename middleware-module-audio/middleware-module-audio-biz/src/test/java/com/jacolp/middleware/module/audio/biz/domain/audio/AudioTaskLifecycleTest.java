package com.jacolp.middleware.module.audio.biz.domain.audio;

import com.jacolp.audio.biz.audio.AudioTaskLifecycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioTaskLifecycleTest {

    @Test
    void preservesPersistedLifecycleCodesAndCasExpectations() {
        assertEquals(0, AudioTaskLifecycle.initialStatus());
        assertEquals(0, AudioTaskLifecycle.callbackStartExpectedStatus());
        assertEquals(1, AudioTaskLifecycle.callbackStartResultStatus());
        assertEquals(1, AudioTaskLifecycle.callbackFinishExpectedStatus());
        assertTrue(AudioTaskLifecycle.canTransition(0, 1));
        assertTrue(AudioTaskLifecycle.canTransition(1, 2));
        assertTrue(AudioTaskLifecycle.canTransition(1, -1));
        assertFalse(AudioTaskLifecycle.canTransition(0, 2));
        assertFalse(AudioTaskLifecycle.canTransition(2, 1));
        assertTrue(AudioTaskLifecycle.isCancellable(0));
        assertTrue(AudioTaskLifecycle.isCancellable(1));
        assertFalse(AudioTaskLifecycle.isCancellable(2));
        assertEquals(-3, AudioTaskLifecycle.cancelledStatus());
    }

    @Test
    void acceptsOnlyTerminalCallbackStatusesAndCompletesOnlySuccess() {
        assertTrue(AudioTaskLifecycle.isAllowedFinishStatus(2));
        assertTrue(AudioTaskLifecycle.isAllowedFinishStatus(-1));
        assertFalse(AudioTaskLifecycle.isAllowedFinishStatus(0));
        assertFalse(AudioTaskLifecycle.isAllowedFinishStatus(1));
        assertTrue(AudioTaskLifecycle.shouldSetCompletedDate(2));
        assertFalse(AudioTaskLifecycle.shouldSetCompletedDate(-1));
    }
}
