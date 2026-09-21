package com.jacolp.audit.application.service;

import com.jacolp.audit.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.common.core.constant.AuditConstant;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.AuditReviewedEvent;
import com.jacolp.common.messaging.pulisher.OutboxEventPublisher;
import com.jacolp.audit.api.AuditTargetType;
import com.jacolp.audit.application.dto.AuditBatchReviewDTO;
import com.jacolp.audit.application.dto.AuditReviewContext;
import com.jacolp.audit.domain.audit.AuditReviewPolicy;
import com.jacolp.audit.domain.audit.AuditReviewPolicy.Outcome;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AuditReviewService {
    private final MetaAuditMapper metaAuditMapper;
    private final ImageAuditMapper imageAuditMapper;
    private final NoteAuditMapper noteAuditMapper;
    private final OutboxEventPublisher eventPublisher;
    private final AuditQueryProjectionMapper projections;

    public AuditReviewService(MetaAuditMapper metaAuditMapper, ImageAuditMapper imageAuditMapper,
                              NoteAuditMapper noteAuditMapper, OutboxEventPublisher eventPublisher,
                              AuditQueryProjectionMapper projections) {
        this.metaAuditMapper = metaAuditMapper;
        this.imageAuditMapper = imageAuditMapper;
        this.noteAuditMapper = noteAuditMapper;
        this.eventPublisher = eventPublisher;
        this.projections = projections;
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchReviewMeta(AuditBatchReviewDTO dto) {
        AuditReviewContext context = validateReviewRequest(dto, AuditTargetType.TAG);
        List<MetaAuditRecordDO> pendingRecords = metaAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) return 0;
        List<Long> reviewIds = pendingRecords.stream().map(MetaAuditRecordDO::getId).toList();
        validateAffected(pendingRecords.size(), metaAuditMapper.batchReviewByIds(reviewIds,
                context.getStatus(), context.getReviewerUserId(), context.getRejectReason()));
        captureReviewer(AuditTargetType.TAG, reviewIds, context.getReviewerUserId());
        publishReviewed(pendingRecords.stream().map(record -> reviewed(record.getId(),
                AuditReviewedEvent.TargetType.TAG, record.getTargetId(), context)).toList());
        return pendingRecords.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchReviewImage(AuditBatchReviewDTO dto) {
        AuditReviewContext context = validateReviewRequest(dto, AuditTargetType.IMAGE);
        List<ImageAuditRecordDO> pendingRecords = imageAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) return 0;
        List<Long> reviewIds = pendingRecords.stream().map(ImageAuditRecordDO::getId).toList();
        validateAffected(pendingRecords.size(), imageAuditMapper.batchReviewByIds(reviewIds,
                context.getStatus(), context.getReviewerUserId(), context.getRejectReason()));
        captureReviewer(AuditTargetType.IMAGE, reviewIds, context.getReviewerUserId());
        publishReviewed(pendingRecords.stream().map(record -> reviewed(record.getId(),
                AuditReviewedEvent.TargetType.IMAGE, record.getImageId(), context)).toList());
        return pendingRecords.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchReviewNote(AuditBatchReviewDTO dto) {
        AuditReviewContext context = validateReviewRequest(dto, AuditTargetType.NOTE);
        List<NoteAuditRecordDO> pendingRecords = noteAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) return 0;
        List<Long> reviewIds = pendingRecords.stream().map(NoteAuditRecordDO::getId).toList();
        validateAffected(pendingRecords.size(), noteAuditMapper.batchReviewByIds(reviewIds,
                context.getStatus(), context.getReviewerUserId(), context.getRejectReason()));
        captureReviewer(AuditTargetType.NOTE, reviewIds, context.getReviewerUserId());
        publishReviewed(pendingRecords.stream().map(record -> reviewed(record.getId(),
                AuditReviewedEvent.TargetType.NOTE, record.getNoteId(), context)).toList());
        return pendingRecords.size();
    }

    private static AuditReviewContext validateReviewRequest(AuditBatchReviewDTO dto, AuditTargetType targetType) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BaseException("审核记录 ID 列表不能为空");
        }
        if (!AuditReviewPolicy.isReviewResultAllowed(targetType, dto.getStatus())) {
            throw new BaseException("无效的审核状态");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : dto.getIds()) if (id != null && id > 0) ids.add(id);
        if (ids.isEmpty()) throw new BaseException("审核记录 ID 列表不能为空");
        Short rejectCode = AuditReviewPolicy.resultStatus(targetType, Outcome.REJECTED);
        String rejectReason = rejectCode.equals(dto.getStatus())
                ? (StringUtils.hasText(dto.getRejectReason())
                    ? dto.getRejectReason().trim() : AuditConstant.DEFAULT_REJECT_REASON)
                : null;
        return new AuditReviewContext(new ArrayList<>(ids), dto.getStatus(),
                BaseContext.getCurrentId(), rejectReason);
    }

    private AuditReviewedEvent reviewed(Long auditId, AuditReviewedEvent.TargetType targetType,
                                        Long targetId, AuditReviewContext context) {
        if (auditId == null || targetId == null) {
            throw new BaseException("审核记录缺少目标标识");
        }
        Outcome outcome = AuditReviewPolicy.outcome(toTargetType(targetType), context.getStatus());
        return new AuditReviewedEvent(auditId, targetType, targetId,
                outcome == Outcome.APPROVED
                        ? AuditReviewedEvent.Decision.APPROVED : AuditReviewedEvent.Decision.REJECTED,
                context.getReviewerUserId(), context.getRejectReason(), Instant.now());
    }

    private void publishReviewed(List<AuditReviewedEvent> events) {
        if (events.isEmpty()) return;
        eventPublisher.publishPartitioned(EventTypes.AUDIT_REVIEWED, EventTypes.AUDIT_REVIEWED,
                "AUDIT_REVIEW", events.getFirst().auditId(), UUID.randomUUID().toString(), events);
    }

    private void captureReviewer(AuditTargetType targetType, List<Long> auditIds, long reviewerUserId) {
        String username = projections.selectUsername(reviewerUserId);
        for (Long auditId : auditIds) {
            projections.captureReviewer(targetType.name(), auditId, username);
        }
    }

    private static AuditTargetType toTargetType(AuditReviewedEvent.TargetType targetType) {
        return switch (targetType) {
            case NOTE -> AuditTargetType.NOTE;
            case TAG -> AuditTargetType.TAG;
            case IMAGE -> AuditTargetType.IMAGE;
        };
    }

    private static void validateAffected(int pendingRecords, int affected) {
        if (pendingRecords != affected) {
            log.error("Pending audit record count differs from the rows actually reviewed");
            throw new BaseException("待审核记录数与实际处理数不一致，请重试");
        }
    }
}
