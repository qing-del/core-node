package com.jacolp.middleware.messaging.base;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Inserts an outbox record.
     * @param envelope the event envelope
     * @param routingKey the routing key
     * @param serializedEnvelope the serialized event envelope
     */
    public void insert(EventEnvelope envelope, String routingKey, String serializedEnvelope) {
        jdbcTemplate.update("""
                INSERT INTO sys_event_outbox
                    (event_id, event_type, routing_key, payload, status, retry_count,
                     next_retry_time, create_time)
                VALUES (?, ?, ?, ?, 'PENDING', 0, NOW(), NOW())
                """, envelope.eventId(), envelope.eventType(), routingKey, serializedEnvelope);
    }

    /**
     * Claims a batch of outbox records for processing.
     * @param claimant the claimant identity
     * @param limit the maximum number of records to claim
     * @param claimSeconds the number of seconds to claim the records for
     * @return the claimed records
     */
    public List<OutboxRecord> claimBatch(String claimant, int limit, long claimSeconds) {
        List<OutboxRecord> claimed = transactionTemplate.execute(status -> {
            // 将 PROCESSING 状态且 claim_until（归属权） 已过期的记录重置为 RETRY
            jdbcTemplate.update("""
                    UPDATE sys_event_outbox
                    SET status = 'RETRY', 
                        claimed_by = NULL, 
                        claim_until = NULL,
                        next_retry_time = NOW()
                    WHERE status = 'PROCESSING' AND claim_until < NOW()
                    """);
            List<OutboxRecord> rows = jdbcTemplate.query("""
                    SELECT id, event_id, event_type, routing_key, payload, retry_count, create_time
                    FROM sys_event_outbox
                    WHERE status IN ('PENDING', 'RETRY') AND next_retry_time <= NOW()
                    ORDER BY id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """, OutboxRepository::mapRecord, limit);
            if (rows.isEmpty()) {
                return List.of();
            }
            String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));  // 有多少个 rows 就有多少个占位符
            Object[] parameters = new Object[rows.size() + 2];  // 将 + 2 加进去是因为需要放置 claimant 和 claimSeconds
            parameters[0] = claimant;
            parameters[1] = claimSeconds;
            for (int i = 0; i < rows.size(); i++) {
                parameters[i + 2] = rows.get(i).id();
            }
            jdbcTemplate.update("""
                    UPDATE sys_event_outbox
                    SET status = 'PROCESSING',
                        claimed_by = ?,
                        claim_until = DATE_ADD(NOW(), INTERVAL ? SECOND)
                    WHERE id IN (%s)
                    """.formatted(placeholders), parameters);
            return rows;
        });
        return claimed == null ? List.of() : claimed;
    }

    /**
     * Marks the outbox record as published, only if it is currently claimed by the given claimant.
     * @param id the outbox record ID
     * @param claimant the claimant identity
     * @return true if the record was marked as published, false otherwise
     */
    public boolean markPublished(long id, String claimant) {
        return jdbcTemplate.update("""
                UPDATE sys_event_outbox
                SET status = 'PUBLISHED', published_time = NOW(), claimed_by = NULL,
                    claim_until = NULL, last_error = NULL
                WHERE id = ? AND status = 'PROCESSING' AND claimed_by = ?
                """, id, claimant) == 1;
    }

    /**
     * Marks the outbox record as failed, only if it is currently claimed by the given claimant.
     * @param id the outbox record ID
     * @param claimant the claimant identity
     * @param retryCount the number of retries
     * @param nextRetry the time when the record will be retried
     * @param error the error message
     * @param dead true if the record is dead, false otherwise
     * @return true if the record was marked as failed, false otherwise
     */
    public boolean markFailed(long id, String claimant, int retryCount, LocalDateTime nextRetry,
                              String error, boolean dead) {
        return jdbcTemplate.update("""
                UPDATE sys_event_outbox
                SET status = ?, retry_count = ?, next_retry_time = ?, last_error = ?,
                    claimed_by = NULL, claim_until = NULL
                WHERE id = ? AND status = 'PROCESSING' AND claimed_by = ?
                """, dead ? "DEAD" : "RETRY", retryCount, nextRetry, truncate(error), id, claimant) == 1;
    }

    /**
     * Maps a row from the outbox table to an OutboxRecord.
     * @param rs the result set
     * @param rowNum the row number
     * @return the outbox record
     */
    private static OutboxRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRecord(rs.getLong("id"), rs.getString("event_id"), rs.getString("event_type"),
                rs.getString("routing_key"), rs.getString("payload"), rs.getInt("retry_count"),
                rs.getTimestamp("create_time").toLocalDateTime());
    }

    /**
     * Truncates an error message to 2000 characters.
     * @param error the error message
     * @return the truncated error message
     */
    private static String truncate(String error) {
        if (error == null) return null;
        return error.length() <= 2000 ? error : error.substring(0, 2000);
    }
}
