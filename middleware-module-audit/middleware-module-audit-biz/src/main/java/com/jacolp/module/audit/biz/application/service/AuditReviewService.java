package com.jacolp.module.audit.biz.application.service;

import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.module.audit.biz.application.dto.AuditBatchReviewDTO;
import com.jacolp.module.audit.biz.application.dto.AuditReviewContext;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.biz.domain.audit.AuditReviewPolicy;
import com.jacolp.module.audit.biz.domain.audit.AuditReviewPolicy.Outcome;
import com.jacolp.module.audit.biz.domain.audit.AuditReviewPolicy.ReviewMode;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.module.media.api.MediaAuditApplyApi;
import com.jacolp.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.module.media.api.model.MediaAuditDecision;
import com.jacolp.module.note.api.NoteAuditApplyApi;
import com.jacolp.module.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.module.note.api.command.ApplyTagAuditCommand;
import com.jacolp.module.note.api.model.AuditDecision;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final NoteAuditApplyApi noteAuditApplyApi;
    private final MediaAuditApplyApi mediaAuditApplyApi;

    public AuditReviewService(MetaAuditMapper metaAuditMapper, ImageAuditMapper imageAuditMapper,
                              NoteAuditMapper noteAuditMapper, NoteAuditApplyApi noteAuditApplyApi,
                              MediaAuditApplyApi mediaAuditApplyApi) {
        this.metaAuditMapper = metaAuditMapper;
        this.imageAuditMapper = imageAuditMapper;
        this.noteAuditMapper = noteAuditMapper;
        this.noteAuditApplyApi = noteAuditApplyApi;
        this.mediaAuditApplyApi = mediaAuditApplyApi;
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchReviewMeta(AuditBatchReviewDTO dto) {
        AuditReviewContext context = validateReviewRequest(dto, AuditTargetType.TAG);
        List<MetaAuditRecordDO> pendingRecords = metaAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) return 0;
        List<Long> reviewIds = pendingRecords.stream().map(MetaAuditRecordDO::getId).toList();
        validateAffected(pendingRecords.size(), metaAuditMapper.batchReviewByIds(reviewIds, context.getStatus(), context.getReviewerUserId(), context.getRejectReason()));
        List<Long> tagIds = pendingRecords.stream().map(MetaAuditRecordDO::getTargetId).filter(id -> id != null).toList();
        if (!tagIds.isEmpty()) {
            int updated = noteAuditApplyApi.applyTagAudit(new ApplyTagAuditCommand(tagIds,
                    toDecision(AuditReviewPolicy.outcome(AuditTargetType.TAG, context.getStatus())))).tagRowsUpdated();
            if (updated < pendingRecords.size()) {
                log.error("Failed to update tag status! : {}", tagIds);
                throw new BaseException("更新标签状态失败");
            }
        }
        return pendingRecords.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchReviewImage(AuditBatchReviewDTO dto) {
        AuditReviewContext context = validateReviewRequest(dto, AuditTargetType.IMAGE);
        List<ImageAuditRecordDO> pendingRecords = imageAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) return 0;
        List<Long> reviewIds = pendingRecords.stream().map(ImageAuditRecordDO::getId).toList();
        validateAffected(pendingRecords.size(), imageAuditMapper.batchReviewByIds(reviewIds, context.getStatus(), context.getReviewerUserId(), context.getRejectReason()));
        List<Long> imageIds = pendingRecords.stream().map(ImageAuditRecordDO::getImageId).filter(id -> id != null).toList();
        if (!imageIds.isEmpty()) {
            int updated = mediaAuditApplyApi.applyMediaAudit(
                    new ApplyMediaAuditCommand(imageIds,
                            toMediaDecision(AuditReviewPolicy.outcome(AuditTargetType.IMAGE, context.getStatus())),
                            AuditReviewPolicy.shouldUpdateRelationStatus(ReviewMode.BATCH))).mediaRowsUpdated();
            if (updated < pendingRecords.size()) {
                log.error("Failed to update image status! : {}", imageIds);
                throw new BaseException("更新图片状态失败");
            }
        }
        return pendingRecords.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchReviewNote(AuditBatchReviewDTO dto) {
        AuditReviewContext context = validateReviewRequest(dto, AuditTargetType.NOTE);
        List<NoteAuditRecordDO> pendingRecords = noteAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) return 0;
        List<Long> reviewIds = pendingRecords.stream().map(NoteAuditRecordDO::getId).toList();
        validateAffected(pendingRecords.size(), noteAuditMapper.batchReviewByIds(reviewIds, context.getStatus(), context.getReviewerUserId(), context.getRejectReason()));
        List<Long> noteIds = pendingRecords.stream().map(NoteAuditRecordDO::getNoteId).filter(id -> id != null).toList();
        if (!noteIds.isEmpty()) {
            int updated = noteAuditApplyApi.applyNoteAudit(new ApplyNoteAuditCommand(noteIds,
                    toDecision(AuditReviewPolicy.outcome(AuditTargetType.NOTE, context.getStatus())))).noteRowsUpdated();
            if (updated < pendingRecords.size()) {
                log.error("Failed to update note status! : {}", noteIds);
                throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
            }
        }
        return pendingRecords.size();
    }

    private static AuditReviewContext validateReviewRequest(AuditBatchReviewDTO dto, AuditTargetType targetType) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) throw new BaseException("审核记录ID列表不能为空");
        boolean validStatus = AuditReviewPolicy.isReviewResultAllowed(targetType, dto.getStatus());
        if (!validStatus) throw new BaseException("无效的审核状态");
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : dto.getIds()) if (id != null && id > 0) ids.add(id);
        if (ids.isEmpty()) throw new BaseException("审核记录ID列表不能为空");
        Short rejectCode = AuditReviewPolicy.resultStatus(targetType, Outcome.REJECTED);
        String rejectReason = rejectCode.equals(dto.getStatus())
                ? (StringUtils.hasText(dto.getRejectReason()) ? dto.getRejectReason().trim() : AuditConstant.DEFAULT_REJECT_REASON)
                : null;
        return new AuditReviewContext(new ArrayList<>(ids), dto.getStatus(), BaseContext.getCurrentId(), rejectReason);
    }

    private static AuditDecision toDecision(Outcome outcome) {
        return outcome == Outcome.APPROVED ? AuditDecision.APPROVED : AuditDecision.REJECTED;
    }

    private static MediaAuditDecision toMediaDecision(Outcome outcome) {
        return outcome == Outcome.APPROVED ? MediaAuditDecision.APPROVED : MediaAuditDecision.REJECTED;
    }

    private static void validateAffected(int pendingRecords, int affected) {
        if (pendingRecords != affected) {
            log.error("Wait to audit the number of records isn't same as real handler number, please check again!");
            throw new BaseException("待审核记录数与实际处理数不一致，请检查！");
        }
    }
}
