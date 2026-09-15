package com.jacolp.middleware.module.audit.biz.application.service;

import com.github.pagehelper.PageHelper;
import com.jacolp.audit.application.dto.ImageAuditListDTO;
import com.jacolp.audit.application.dto.MetaAuditListDTO;
import com.jacolp.audit.application.dto.NoteAuditListDTO;
import com.jacolp.audit.application.service.AuditQueryService;
import com.jacolp.audit.application.vo.ImageAuditVO;
import com.jacolp.audit.application.vo.MetaAuditVO;
import com.jacolp.audit.application.vo.NoteAuditVO;
import com.jacolp.audit.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.NoteAuditMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditQueryServiceTest {
    private MetaAuditMapper metaMapper;
    private ImageAuditMapper imageMapper;
    private NoteAuditMapper noteMapper;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        metaMapper = mock(MetaAuditMapper.class);
        imageMapper = mock(ImageAuditMapper.class);
        noteMapper = mock(NoteAuditMapper.class);
        service = new AuditQueryService(metaMapper, imageMapper, noteMapper);
    }

    @AfterEach
    void clearPage() {
        PageHelper.clearPage();
    }

    @Test
    void metaListReturnsProjectionFieldsSelectedByAuditMapper() {
        MetaAuditVO record = new MetaAuditVO();
        record.setApplicantUsername("applicant");
        record.setReviewerUsername("reviewer");
        record.setTargetName("tag");
        when(metaMapper.listByCondition(null, null, null)).thenReturn(List.of(record));

        assertThat(service.listMetaAudits(new MetaAuditListDTO()).getRecords()).containsExactly(record);
    }

    @Test
    void imageListReturnsProjectionFieldsSelectedByAuditMapper() {
        ImageAuditVO record = new ImageAuditVO();
        record.setApplicantUsername("applicant");
        record.setReviewerUsername("reviewer");
        record.setFilename("image.png");
        record.setOssUrl("https://cdn.example/image.png");
        when(imageMapper.listByCondition(null, null)).thenReturn(List.of(record));

        assertThat(service.listImageAudits(new ImageAuditListDTO()).getRecords()).containsExactly(record);
    }

    @Test
    void noteListReturnsProjectionFieldsSelectedByAuditMapper() {
        NoteAuditVO record = new NoteAuditVO();
        record.setApplicantUsername("applicant");
        record.setReviewerUsername("reviewer");
        record.setNoteTitle("note");
        when(noteMapper.listByCondition(null, null)).thenReturn(List.of(record));

        assertThat(service.listNoteAudits(new NoteAuditListDTO()).getRecords()).containsExactly(record);
    }
}
