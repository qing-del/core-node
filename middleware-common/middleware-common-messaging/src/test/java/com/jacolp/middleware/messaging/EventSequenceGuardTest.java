package com.jacolp.middleware.messaging;

import com.jacolp.common.messaging.base.EventSequenceGuard;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventSequenceGuardTest {
    @Test
    void olderOrDuplicateSequenceCannotAdvanceProjection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class),
                eq("consumer"), eq("IMAGE"), eq(7L))).thenReturn(12L);
        EventSequenceGuard guard = new EventSequenceGuard(jdbcTemplate);

        assertThat(guard.advance("consumer", "IMAGE", 7L, 11L)).isFalse();
        assertThat(guard.advance("consumer", "IMAGE", 7L, 12L)).isFalse();
        verify(jdbcTemplate, never()).update(anyString(), eq(11L), eq("consumer"),
                eq("IMAGE"), eq(7L), eq(11L));
    }

    @Test
    void newerSequenceAdvancesProjectionExactlyOnce() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class),
                eq("consumer"), eq("IMAGE"), eq(7L))).thenReturn(12L);
        when(jdbcTemplate.update(anyString(), eq(13L), eq("consumer"),
                eq("IMAGE"), eq(7L), eq(13L))).thenReturn(1);
        EventSequenceGuard guard = new EventSequenceGuard(jdbcTemplate);

        assertThat(guard.advance("consumer", "IMAGE", 7L, 13L)).isTrue();
    }
}
