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
import com.jacolp.module.media.api.MediaFileApi;
import com.jacolp.module.media.api.model.MediaFileSummary;
import com.jacolp.module.note.api.NoteReadApi;
import com.jacolp.module.note.api.model.NoteSummary;
import com.jacolp.module.note.api.model.TagSummary;
import com.jacolp.module.system.api.UserProfileApi;
import com.jacolp.result.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** Paged audit-record queries with cross-module display fields filled in one batch per owner API. */
@Service
public class AuditQueryService {
    private final MetaAuditMapper metaAuditMapper;
    private final ImageAuditMapper imageAuditMapper;
    private final NoteAuditMapper noteAuditMapper;
    private final UserProfileApi userProfileApi;
    private final NoteReadApi noteReadApi;
    private final MediaFileApi mediaFileApi;

    public AuditQueryService(MetaAuditMapper metaAuditMapper, ImageAuditMapper imageAuditMapper,
                             NoteAuditMapper noteAuditMapper, UserProfileApi userProfileApi,
                             NoteReadApi noteReadApi, MediaFileApi mediaFileApi) {
        this.metaAuditMapper = metaAuditMapper;
        this.imageAuditMapper = imageAuditMapper;
        this.noteAuditMapper = noteAuditMapper;
        this.userProfileApi = userProfileApi;
        this.noteReadApi = noteReadApi;
        this.mediaFileApi = mediaFileApi;
    }

    public PageResult listMetaAudits(MetaAuditListDTO dto) {
        MetaAuditListDTO query = dto == null ? new MetaAuditListDTO() : dto;
        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<MetaAuditVO> records = metaAuditMapper.listByCondition(query.getApplyType(), query.getStatus(), query.getApplicantUserId());
        Map<Long, UserProfileApi.UserProfile> profiles = findProfiles(records, MetaAuditVO::getApplicantUserId, MetaAuditVO::getReviewerUserId);
        Map<Long, TagSummary> tags = findTags(records.stream().map(MetaAuditVO::getTargetId).toList());
        records.forEach(record -> {
            record.setApplicantUsername(username(profiles, record.getApplicantUserId()));
            record.setReviewerUsername(username(profiles, record.getReviewerUserId()));
            TagSummary tag = tags.get(record.getTargetId());
            record.setTargetName(tag == null ? null : tag.name());
        });
        return page(records);
    }

    public PageResult listImageAudits(ImageAuditListDTO dto) {
        ImageAuditListDTO query = dto == null ? new ImageAuditListDTO() : dto;
        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<ImageAuditVO> records = imageAuditMapper.listByCondition(query.getStatus(), query.getApplicantUserId());
        Map<Long, UserProfileApi.UserProfile> profiles = findProfiles(records, ImageAuditVO::getApplicantUserId, ImageAuditVO::getReviewerUserId);
        Map<Long, MediaFileSummary> images = findImages(records.stream().map(ImageAuditVO::getImageId).toList());
        records.forEach(record -> {
            record.setApplicantUsername(username(profiles, record.getApplicantUserId()));
            record.setReviewerUsername(username(profiles, record.getReviewerUserId()));
            MediaFileSummary image = images.get(record.getImageId());
            record.setFilename(image == null ? null : image.filename());
            record.setOssUrl(image == null ? null : image.url());
        });
        return page(records);
    }

    public PageResult listNoteAudits(NoteAuditListDTO dto) {
        NoteAuditListDTO query = dto == null ? new NoteAuditListDTO() : dto;
        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<NoteAuditVO> records = noteAuditMapper.listByCondition(query.getStatus(), query.getApplicantUserId());
        Map<Long, UserProfileApi.UserProfile> profiles = findProfiles(records, NoteAuditVO::getApplicantUserId, NoteAuditVO::getReviewerUserId);
        Map<Long, NoteSummary> notes = findNotes(records.stream().map(NoteAuditVO::getNoteId).toList());
        records.forEach(record -> {
            record.setApplicantUsername(username(profiles, record.getApplicantUserId()));
            record.setReviewerUsername(username(profiles, record.getReviewerUserId()));
            NoteSummary note = notes.get(record.getNoteId());
            record.setNoteTitle(note == null ? null : note.title());
        });
        return page(records);
    }

    private <T> Map<Long, UserProfileApi.UserProfile> findProfiles(Collection<T> records,
            Function<T, Long> applicant, Function<T, Long> reviewer) {
        List<Long> ids = records.stream().flatMap(record -> java.util.stream.Stream.of(applicant.apply(record), reviewer.apply(record)))
                .filter(id -> id != null).distinct().toList();
        return ids.isEmpty() ? Map.of() : userProfileApi.getProfilesByIds(ids);
    }

    private Map<Long, TagSummary> findTags(Collection<Long> ids) {
        List<Long> targetIds = ids.stream().filter(id -> id != null).distinct().toList();
        return targetIds.isEmpty() ? Map.of() : noteReadApi.findTagSummariesByIds(targetIds);
    }

    private Map<Long, NoteSummary> findNotes(Collection<Long> ids) {
        List<Long> targetIds = ids.stream().filter(id -> id != null).distinct().toList();
        return targetIds.isEmpty() ? Map.of() : noteReadApi.findNoteSummariesByIds(targetIds);
    }

    private Map<Long, MediaFileSummary> findImages(Collection<Long> ids) {
        List<Long> targetIds = ids.stream().filter(id -> id != null).distinct().toList();
        return targetIds.isEmpty() ? Map.of() : mediaFileApi.findByIds(targetIds);
    }

    private static String username(Map<Long, UserProfileApi.UserProfile> profiles, Long userId) {
        UserProfileApi.UserProfile profile = profiles.get(userId);
        return profile == null ? null : profile.username();
    }

    private static PageResult page(List<?> records) {
        PageInfo<?> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }
}
