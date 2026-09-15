package com.jacolp.module.audit.biz.application.api;

import com.jacolp.exception.BaseException;
import com.jacolp.module.audit.api.AuditApplicationApi;
import com.jacolp.module.audit.api.AuditApplicationResult;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.module.audit.api.CancelAuditApplicationResult;
import com.jacolp.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.module.audit.api.PendingAuditApplicationQuery;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.module.audit.biz.domain.audit.AuditReviewPolicy;
import java.util.Objects;

import org.springframework.stereotype.Service;

/** Formal audit-owned implementation backed by the three legacy audit tables. */
@Service
public class AuditApplicationApiService implements AuditApplicationApi {
    private static final short TAG_APPLY_TYPE = 2;
    private final MetaAuditMapper metaAuditMapper;
    private final ImageAuditMapper imageAuditMapper;
    private final NoteAuditMapper noteAuditMapper;

    public AuditApplicationApiService(MetaAuditMapper metaAuditMapper, ImageAuditMapper imageAuditMapper,
                                      NoteAuditMapper noteAuditMapper) {
        this.metaAuditMapper = metaAuditMapper;
        this.imageAuditMapper = imageAuditMapper;
        this.noteAuditMapper = noteAuditMapper;
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
        return cancelledResult(command, metaAuditMapper.deletePendingByApplyTypeAndTargetId(TAG_APPLY_TYPE, command.targetId()));
    }

    private CancelAuditApplicationResult cancelImageApplication(CancelAuditApplicationCommand command) {
        ImageAuditRecordDO record = imageAuditMapper.selectPendingByImageId(command.targetId());
        ensurePendingApplicant(record == null ? null : record.getApplicantUserId(), record == null ? null : record.getStatus(), AuditTargetType.IMAGE, command.actorUserId());
        return cancelledResult(command, imageAuditMapper.deletePendingByImageId(command.targetId()));
    }

    private CancelAuditApplicationResult cancelNoteApplication(CancelAuditApplicationCommand command) {
        NoteAuditRecordDO record = noteAuditMapper.selectPendingByNoteId(command.targetId());
        ensurePendingApplicant(record == null ? null : record.getApplicantUserId(), record == null ? null : record.getStatus(), AuditTargetType.NOTE, command.actorUserId());
        return cancelledResult(command, noteAuditMapper.deletePendingByNoteId(command.targetId()));
    }

    private static AuditApplicationResult createdResult(CreateAuditApplicationCommand command, Long auditApplicationId) {
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
