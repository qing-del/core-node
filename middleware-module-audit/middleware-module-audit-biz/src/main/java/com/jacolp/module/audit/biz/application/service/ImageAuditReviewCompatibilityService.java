package com.jacolp.module.audit.biz.application.service;

import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.messaging.event.AuditReviewedEvent;
import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.pulisher.OutboxEventPublisher;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.biz.application.dto.ImageAuditReviewDTO;
import com.jacolp.module.audit.biz.domain.audit.AuditReviewPolicy;
import com.jacolp.module.audit.biz.domain.audit.AuditReviewPolicy.Outcome;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility endpoint backed by the same outbox event flow as batch review. */
@Service
public class ImageAuditReviewCompatibilityService {
    private static final String IMAGE_NOT_FOUND = "未找到目标图片";
    private static final String AUDIT_ALREADY_PROCESSED = "该审核记录已处理";
    private static final String REJECT_REASON_REQUIRED = "拒绝原因不能为空";

    private final ImageAuditMapper imageAuditMapper;
    private final OutboxEventPublisher eventPublisher;
    private final AuditQueryProjectionMapper projections;

    public ImageAuditReviewCompatibilityService(ImageAuditMapper imageAuditMapper,
                                                OutboxEventPublisher eventPublisher,
                                                AuditQueryProjectionMapper projections) {
        this.imageAuditMapper = imageAuditMapper;
        this.eventPublisher = eventPublisher;
        this.projections = projections;
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(ImageAuditReviewDTO dto) {
        if (dto == null || dto.getAuditId() == null || dto.getAuditId() <= 0) {
            throw new BaseException(IMAGE_NOT_FOUND);
        }
        ImageAuditRecordDO record = imageAuditMapper.selectById(dto.getAuditId());
        if (record == null || !AuditReviewPolicy.isPending(AuditTargetType.IMAGE, record.getStatus())) {
            throw new BaseException(AUDIT_ALREADY_PROCESSED);
        }
        if (!dto.getApproved() && (dto.getRejectReason() == null || dto.getRejectReason().isEmpty())) {
            throw new BaseException(REJECT_REASON_REQUIRED);
        }
        Outcome outcome = dto.getApproved() ? Outcome.APPROVED : Outcome.REJECTED;
        record.setStatus(AuditReviewPolicy.resultStatus(AuditTargetType.IMAGE, outcome));
        record.setReviewerUserId(BaseContext.getCurrentId());
        record.setReviewTime(LocalDateTime.now());
        record.setRejectReason(dto.getApproved() ? null : dto.getRejectReason());
        if (imageAuditMapper.updateAuditRecord(record) != 1) {
            throw new BaseException(AUDIT_ALREADY_PROCESSED);
        }
        projections.captureReviewer(AuditTargetType.IMAGE.name(), record.getId(),
                projections.selectUsername(record.getReviewerUserId()));
        AuditReviewedEvent event = new AuditReviewedEvent(record.getId(),
                AuditReviewedEvent.TargetType.IMAGE, record.getImageId(),
                outcome == Outcome.APPROVED
                        ? AuditReviewedEvent.Decision.APPROVED : AuditReviewedEvent.Decision.REJECTED,
                record.getReviewerUserId(), record.getRejectReason(), Instant.now());
        eventPublisher.publishPartitioned(EventTypes.AUDIT_REVIEWED, EventTypes.AUDIT_REVIEWED,
                "AUDIT_REVIEW", record.getId(), record.getId().toString(), List.of(event));
    }
}
