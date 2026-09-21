package com.jacolp.common.messaging.base;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Prevents an older event for the same projection aggregate from overwriting a newer one. */
@Service
public class EventSequenceGuard {
    private final JdbcTemplate jdbcTemplate;

    public EventSequenceGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean advance(String consumerName, String aggregateType, long aggregateId, long sequence) {
        if (sequence <= 0) throw new IllegalArgumentException("sequence must be positive");
        Long current = lockCurrent(consumerName, aggregateType, aggregateId);
        if (current != null) {
            if (sequence <= current) return false;
            return jdbcTemplate.update("""
                    UPDATE sys_event_projection_version
                    SET last_sequence = ?, update_time = NOW()
                    WHERE consumer_name = ? AND aggregate_type = ? AND aggregate_id = ?
                      AND last_sequence < ?
                    """, sequence, consumerName, aggregateType, aggregateId, sequence) == 1;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_event_projection_version
                        (consumer_name, aggregate_type, aggregate_id, last_sequence, update_time)
                    VALUES (?, ?, ?, ?, NOW())
                    """, consumerName, aggregateType, aggregateId, sequence);
            return true;
        } catch (DuplicateKeyException raced) {
            return advance(consumerName, aggregateType, aggregateId, sequence);
        }
    }

    private Long lockCurrent(String consumerName, String aggregateType, long aggregateId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT last_sequence
                    FROM sys_event_projection_version
                    WHERE consumer_name = ? AND aggregate_type = ? AND aggregate_id = ?
                    FOR UPDATE
                    """, Long.class, consumerName, aggregateType, aggregateId);
        } catch (EmptyResultDataAccessException missing) {
            return null;
        }
    }
}
