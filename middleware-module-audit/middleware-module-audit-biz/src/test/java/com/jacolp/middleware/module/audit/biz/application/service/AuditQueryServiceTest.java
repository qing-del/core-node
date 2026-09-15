package com.jacolp.middleware.module.audit.biz.application.service;

import com.github.pagehelper.PageHelper;
import com.jacolp.module.audit.biz.application.dto.ImageAuditListDTO;
import com.jacolp.module.audit.biz.application.dto.MetaAuditListDTO;
import com.jacolp.module.audit.biz.application.dto.NoteAuditListDTO;
import com.jacolp.module.audit.biz.application.service.AuditQueryService;
import com.jacolp.module.audit.biz.application.vo.ImageAuditVO;
import com.jacolp.module.audit.biz.application.vo.MetaAuditVO;
import com.jacolp.module.audit.biz.application.vo.NoteAuditVO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.module.media.api.MediaFileApi;
import com.jacolp.module.note.api.NoteReadApi;
import com.jacolp.module.system.api.UserProfileApi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditQueryServiceTest {
    private MetaAuditMapper metaMapper;
    private ImageAuditMapper imageMapper;
    private NoteAuditMapper noteMapper;
    private UserProfileApi userProfileApi;
    private NoteReadApi noteReadApi;
    private MediaFileApi mediaFileApi;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        metaMapper = mock(MetaAuditMapper.class);
        imageMapper = mock(ImageAuditMapper.class);
        noteMapper = mock(NoteAuditMapper.class);
        userProfileApi = mock(UserProfileApi.class);
        noteReadApi = mock(NoteReadApi.class);
        mediaFileApi = mock(MediaFileApi.class);
        service = new AuditQueryService(metaMapper, imageMapper, noteMapper, userProfileApi, noteReadApi, mediaFileApi);
        when(userProfileApi.getProfilesByIds(anyCollection())).thenReturn(Map.of());
        when(noteReadApi.findTagSummariesByIds(anyCollection())).thenReturn(Map.of());
        when(noteReadApi.findNoteSummariesByIds(anyCollection())).thenReturn(Map.of());
        when(mediaFileApi.findByIds(anyCollection())).thenReturn(Map.of());
    }

    @AfterEach
    void clearPage() { PageHelper.clearPage(); }

    @Test
    void metaListUsesOneUserAndOneTagBatchLookup() {
        MetaAuditVO record = new MetaAuditVO();
        record.setApplicantUserId(1L); record.setReviewerUserId(2L); record.setTargetId(3L);
        when(metaMapper.listByCondition(null, null, null)).thenReturn(List.of(record));
        service.listMetaAudits(new MetaAuditListDTO());
        verify(userProfileApi, times(1)).getProfilesByIds(anyCollection());
        verify(noteReadApi, times(1)).findTagSummariesByIds(anyCollection());
    }

    @Test
    void imageListUsesOneUserAndOneMediaBatchLookup() {
        ImageAuditVO record = new ImageAuditVO();
        record.setApplicantUserId(1L); record.setReviewerUserId(2L); record.setImageId(3L);
        when(imageMapper.listByCondition(null, null)).thenReturn(List.of(record));
        service.listImageAudits(new ImageAuditListDTO());
        verify(userProfileApi, times(1)).getProfilesByIds(anyCollection());
        verify(mediaFileApi, times(1)).findByIds(anyCollection());
    }

    @Test
    void noteListUsesOneUserAndOneNoteBatchLookup() {
        NoteAuditVO record = new NoteAuditVO();
        record.setApplicantUserId(1L); record.setReviewerUserId(2L); record.setNoteId(3L);
        when(noteMapper.listByCondition(null, null)).thenReturn(List.of(record));
        service.listNoteAudits(new NoteAuditListDTO());
        verify(userProfileApi, times(1)).getProfilesByIds(anyCollection());
        verify(noteReadApi, times(1)).findNoteSummariesByIds(anyCollection());
    }
}
