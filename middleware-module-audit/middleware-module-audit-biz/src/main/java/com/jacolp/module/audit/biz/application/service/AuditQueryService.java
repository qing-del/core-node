package com.jacolp.module.audit.biz.application.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.module.audit.biz.application.dto.ImageAuditListDTO;
import com.jacolp.module.audit.biz.application.dto.MetaAuditListDTO;
import com.jacolp.module.audit.biz.application.dto.NoteAuditListDTO;
import com.jacolp.module.audit.biz.application.vo.ImageAuditVO;
import com.jacolp.module.audit.biz.application.vo.MetaAuditVO;
import com.jacolp.module.audit.biz.application.vo.NoteAuditVO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.result.PageResult;
import java.util.List;
import org.springframework.stereotype.Service;

/** Paged audit-record queries backed only by audit-owned tables and projections. */
@Service
public class AuditQueryService {
    private final MetaAuditMapper metaAuditMapper;
    private final ImageAuditMapper imageAuditMapper;
    private final NoteAuditMapper noteAuditMapper;

    public AuditQueryService(MetaAuditMapper metaAuditMapper, ImageAuditMapper imageAuditMapper,
                             NoteAuditMapper noteAuditMapper) {
        this.metaAuditMapper = metaAuditMapper;
        this.imageAuditMapper = imageAuditMapper;
        this.noteAuditMapper = noteAuditMapper;
    }

    public PageResult listMetaAudits(MetaAuditListDTO dto) {
        MetaAuditListDTO query = dto == null ? new MetaAuditListDTO() : dto;
        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<MetaAuditVO> records = metaAuditMapper.listByCondition(query.getApplyType(), query.getStatus(), query.getApplicantUserId());
        return page(records);
    }

    public PageResult listImageAudits(ImageAuditListDTO dto) {
        ImageAuditListDTO query = dto == null ? new ImageAuditListDTO() : dto;
        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<ImageAuditVO> records = imageAuditMapper.listByCondition(query.getStatus(), query.getApplicantUserId());
        return page(records);
    }

    public PageResult listNoteAudits(NoteAuditListDTO dto) {
        NoteAuditListDTO query = dto == null ? new NoteAuditListDTO() : dto;
        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<NoteAuditVO> records = noteAuditMapper.listByCondition(query.getStatus(), query.getApplicantUserId());
        return page(records);
    }

    private static PageResult page(List<?> records) {
        PageInfo<?> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }
}
