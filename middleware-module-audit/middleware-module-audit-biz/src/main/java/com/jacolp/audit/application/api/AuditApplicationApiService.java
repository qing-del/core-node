package com.jacolp.audit.application.api;

import com.jacolp.audit.domain.audit.AuditReviewPolicy;
import com.jacolp.audit.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.audit.api.AuditApplicationApi;
import com.jacolp.audit.api.AuditApplicationResult;
import com.jacolp.audit.api.AuditTargetType;
import com.jacolp.audit.api.CancelAuditApplicationCommand;
import com.jacolp.audit.api.CancelAuditApplicationResult;
import com.jacolp.audit.api.CreateAuditApplicationCommand;
import com.jacolp.audit.api.PendingAuditApplicationQuery;

import java.util.Objects;

import org.springframework.stereotype.Service;

/** Formal audit-owned implementation backed by the three legacy audit tables. */
@Service
public class AuditApplicationApiService implements AuditApplicationApi {
    private static final short TAG_APPLY_TYPE = 2;
    private final MetaAuditMapper metaAuditMapper;
    private final ImageAuditMapper imageAuditMapper;
    private final NoteAuditMapper noteAuditMapper;
    private final AuditQueryProjectionMapper projections;

    public AuditApplicationApiService(MetaAuditMapper metaAuditMapper, ImageAuditMapper imageAuditMapper,
                                      NoteAuditMapper noteAuditMapper, AuditQueryProjectionMapper projections) {
        this.metaAuditMapper = metaAuditMapper;
        this.imageAuditMapper = imageAuditMapper;
        this.noteAuditMapper = noteAuditMapper;
        this.projections = projections;
    }

    @Override
    public boolean hasPendingApplication(PendingAuditApplicationQuery query) {
        return switch (query.targetType()) {
            case TAG -> metaAuditMapper.countPendingAuditByApplyTypeAndTargetId(TAG_APPLY_TYPE, query.targetId()) > 0;
            case IMAGE -> imageAuditMapper.countPendingAuditByImageId(query.targetId()) > 0;
            case NOTE -> noteAuditMapper.countPendingAuditByNoteId(query.targetId()) > 0;
        };
    }

    @Override
    public AuditApplicationResult createApplication(CreateAuditApplicationCommand command) {
        if (hasPendingApplication(new PendingAuditApplicationQuery(command.targetType(), command.targetId()))) {
            throw new BaseException("该对象已有待审核的申请");
        }
        return switch (command.targetType()) {
            case TAG -> createTagApplication(command);
            case IMAGE -> createImageApplication(command);
            case NOTE -> createNoteApplication(command);
        };
    }

    @Override
    public CancelAuditApplicationResult cancelApplication(CancelAuditApplicationCommand command) {
        return switch (command.targetType()) {
            case TAG -> cancelTagApplication(command);
            case IMAGE -> cancelImageApplication(command);
            case NOTE -> cancelNoteApplication(command);
        };
    }

    private AuditApplicationResult createTagApplication(CreateAuditApplicationCommand command) {
        MetaAuditRecordDO record = new MetaAuditRecordDO();
        record.setApplyType(TAG_APPLY_TYPE);
        record.setTargetId(command.targetId());
        record.setApplicantUserId(command.applicantUserId());
        record.setApplyReason(command.applyReason());
        ensureInserted(metaAuditMapper.insertAuditRecord(record), record.getId());
        return createdResult(command, record.getId());
    }

    private AuditApplicationResult createImageApplication(CreateAuditApplicationCommand command) {
        ImageAuditRecordDO record = new ImageAuditRecordDO();
        record.setImageId(command.targetId());
        record.setApplicantUserId(command.applicantUserId());
        record.setApplyReason(command.applyReason());
        ensureInserted(imageAuditMapper.insertAuditRecord(record), record.getId());
        return createdResult(command, record.getId());
    }

    private AuditApplicationResult createNoteApplication(CreateAuditApplicationCommand command) {
        NoteAuditRecordDO record = new NoteAuditRecordDO();
        record.setNoteId(command.targetId());
        record.setApplicantUserId(command.applicantUserId());
        record.setApplyReason(command.applyReason());
        ensureInserted(noteAuditMapper.insertAuditRecord(record), record.getId());
        return createdResult(command, record.getId());
    }

    private CancelAuditApplicationResult cancelTagApplication(CancelAuditApplicationCommand command) {
        MetaAuditRecordDO record = metaAuditMapper.selectPendingByApplyTypeAndTargetId(TAG_APPLY_TYPE, command.targetId());
        ensurePendingApplicant(record == null ? null : record.getApplicantUserId(), record == null ? null : record.getStatus(), AuditTargetType.TAG, command.actorUserId());
        return cancelledResult(command, metaAuditMapper.cancelPendingByApplyTypeAndTargetId(TAG_APPLY_TYPE,
                command.targetId(), AuditReviewPolicy.cancelledStatus(AuditTargetType.TAG)));
    }

    private CancelAuditApplicationResult cancelImageApplication(CancelAuditApplicationCommand command) {
        ImageAuditRecordDO record = imageAuditMapper.selectPendingByImageId(command.targetId());
        ensurePendingApplicant(record == null ? null : record.getApplicantUserId(), record == null ? null : record.getStatus(), AuditTargetType.IMAGE, command.actorUserId());
        return cancelledResult(command, imageAuditMapper.cancelPendingByImageId(command.targetId(),
                AuditReviewPolicy.cancelledStatus(AuditTargetType.IMAGE)));
    }

    private CancelAuditApplicationResult cancelNoteApplication(CancelAuditApplicationCommand command) {
        NoteAuditRecordDO record = noteAuditMapper.selectPendingByNoteId(command.targetId());
        ensurePendingApplicant(record == null ? null : record.getApplicantUserId(), record == null ? null : record.getStatus(), AuditTargetType.NOTE, command.actorUserId());
        return cancelledResult(command, noteAuditMapper.cancelPendingByNoteId(command.targetId(),
                AuditReviewPolicy.cancelledStatus(AuditTargetType.NOTE)));
    }

    private AuditApplicationResult createdResult(CreateAuditApplicationCommand command, Long auditApplicationId) {
        projections.upsertRecord(command.targetType().name(), auditApplicationId, command.targetId(),
                projections.selectUsername(command.applicantUserId()), command.targetName(), command.targetUrl());
        return new AuditApplicationResult(auditApplicationId, command.targetType(), command.targetId(), command.applicantUserId());
    }

    private static CancelAuditApplicationResult cancelledResult(CancelAuditApplicationCommand command, int affected) {
        if (affected < 1) throw new BaseException("未找到待审核的申请记录");
        return new CancelAuditApplicationResult(command.targetType(), command.targetId(), command.actorUserId(), affected);
    }

    private static void ensureInserted(int affected, Long auditApplicationId) {
        if (affected < 1 || auditApplicationId == null) throw new BaseException("创建审核申请失败");
    }

    private static void ensurePendingApplicant(Long applicantUserId, Short status, AuditTargetType targetType, Long actorUserId) {
        if (applicantUserId == null) throw new BaseException("未找到待审核的申请记录");
        if (!AuditReviewPolicy.isPending(targetType, status)) throw new BaseException("审核申请未处于待审核状态");
        if (!Objects.equals(applicantUserId, actorUserId)) throw new BaseException("只能撤销自己的审核申请");
    }
}
