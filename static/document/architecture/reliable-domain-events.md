# Reliable cross-module domain events

The audit, note, media, and system modules use a shared transactional Outbox/Inbox layer for cross-module event propagation and side effects. Business code writes its aggregate changes and `sys_event_outbox` row in the same MySQL transaction. It does not wait for RabbitMQ, SMTP, or OSS. Audit application creation and cancellation are the exception: they call the audit module's API synchronously inside the caller's transaction (see "Audit application state transitions").

## Topology and contracts

All messages use the durable topic exchange `middleware.domain.events`, persistent delivery, and `EventEnvelope` fields `eventId`, `eventType`, `schemaVersion`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, and `payload`.

| Routing key / event | Producer | Queue and consumer | Purpose |
| --- | --- | --- | --- |
| `audit.reviewed` | Audit | `middleware.note.events` / `note.audit-reviewed` | Apply NOTE, TAG, and IMAGE relation decisions owned by Note |
| `audit.reviewed` | Audit | `middleware.media.events` / `media.audit-reviewed` | Apply IMAGE decisions owned by Media |
| `storage.released` | Note, Media | `middleware.system.events` / `system.storage-released` | Release storage quota after logical deletion |
| `media.resource.delete-requested` | Media | `middleware.media.resource-delete` / `media.resource-delete` | Delete an OSS object and complete the legacy deletion tracking row |
| `email.send-requested` | System | `middleware.system.email` / `system.email-send` | Render and send activation, verification, and administrator email |
| `user.profile-changed` | System | `middleware.audit.projections` / `audit.user-profile-projection` | Maintain the current username lookup used only when freezing a new audit snapshot |

Every main queue has `<queue>.retry` and `<queue>.dlq`. Note and Media have independent queues, so an IMAGE review is delivered to both owners and failure in either consumer cannot suppress the other.

## Consistency boundaries

- Aggregate mutation plus Outbox insert is one local database transaction. Publishers use mandatory transaction propagation; a missing transaction fails fast.
- `OutboxRelay` claims rows with an owner and lease, sends with publisher confirms, and marks `PUBLISHED` only after broker confirmation. A broker outage leaves committed rows retryable.
- Delivery is at least once. A publish confirm may be lost after the broker accepted a message, so duplicate delivery is expected.
- Each consumer executes its owned mutation and its Inbox success record in one transaction. The unique key `(event_id, consumer_name)` makes redelivery a no-op. Failed business work rolls back the Inbox insert.
- Audit-review consumers additionally use a per-target sequence guard. Older or duplicate audit IDs cannot roll an aggregate back after a newer decision.
- Large review batches are split according to `jacolp.messaging.shard-size`; each shard has its own envelope and event ID.

No public event carries another module's database status code. Wire decisions are business values such as `APPROVED`, `REJECTED`, and `DELETED`; each owner maps them to its local state.

## Audit application state transitions

Audit application creation and cancellation are synchronous and transactional: the owner module calls `AuditApplicationApi` inside the same database transaction as its own state change, so both sides commit or roll back together. No Outbox round-trip is involved.

- Submit: the owner CASes its target status to pending (`UPDATE ... SET status = pending WHERE id = ? AND status = expected`), then `createApplication` inserts the audit record with pending status and freezes the display snapshot. The guarded status update serializes concurrent submits — exactly one caller wins.
- Cancel: `cancelApplication` first runs a guarded UPDATE on the audit record (`status = pending -> cancelled`, must affect exactly one row), then the owner CASes its target status back to the pre-pending state. The row-level guard serializes cancel against review: the loser affects 0 rows and its whole transaction rolls back, so the target status and the audit record can never diverge.
- Review is unchanged: the guarded record update (`pending -> approved/rejected`) commits, then `audit.reviewed` is published through the Outbox and each owner applies the decision with a conditional update on its target status.

Audit list display data is historical. `audit_query_record_projection` is keyed by `(target_type, audit_id)`: applicant and target fields are frozen when the application is created, and reviewer username is frozen when review completes. `20260730_audit_query_projection.sql` backfills old records once; runtime list queries join only audit-owned tables. Missing legacy snapshots are tolerated and return null display fields rather than triggering synchronous owner-module calls.

## Retry and failure handling

Outbox publication uses exponential backoff from `jacolp.messaging.initial-backoff` up to `max-backoff`, bounded by `max-retries`. Consumer failures are republished with `x-application-retry-count` to the TTL retry queue; after the limit, the message goes to the durable DLQ. The `x-last-error` header is truncated to a safe diagnostic length.

SMTP and OSS calls happen only in their dedicated consumers. Inbox identity suppresses redelivery after a committed successful send, and one envelope per recipient prevents one SMTP failure from replaying other recipients. As with any non-transactional SMTP provider, a process crash after the provider accepts a message but before the Inbox commit can still produce one duplicate; the `businessKey` is retained for diagnosis and controlled resend. OSS deletion treats an already-absent object as success through the storage adapter contract. Existing image deletion tracking rows remain compatible: pending rows are queued into the Outbox flow, and terminal failure is retained for manual repair.

## Operations

1. Check `sys_event_outbox` by `status`, `retry_count`, and `next_retry_time`. `PENDING`/claimed rows during a RabbitMQ outage are expected; old claimed rows become eligible after `claim-seconds`.
2. Check each `<queue>.dlq`, the `x-last-error` header, and application logs. Repair the underlying data or dependency before replaying a DLQ message to its main queue.
3. Check `sys_event_inbox` by `event_id` and `consumer_name` before assuming a replay should mutate data.
4. For image deletion, inspect `biz_image_delete_dead_letter` together with the media deletion queue and DLQ.
5. For an audit display mismatch, inspect `audit_query_record_projection`. Do not repair it by reintroducing synchronous calls in `AuditQueryService`.

Important tuning properties are `batch-size`, `shard-size`, `poll-delay-ms`, `claim-seconds`, `confirm-timeout-ms`, `retry-queue-delay-ms`, `max-retries`, `initial-backoff`, `max-backoff`, and `max-payload-bytes` under `jacolp.messaging`.
