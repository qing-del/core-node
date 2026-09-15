package com.jacolp.note.application.service;

import java.util.List;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.infrastructure.cache.GuestCacheConstant;
import com.jacolp.note.infrastructure.cache.GuestCacheable;
import com.jacolp.note.infrastructure.persistence.mapper.NoteEachMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteTagMappingMapper;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.note.application.dto.note.PublicNoteQueryDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.NoteConstant;
import com.jacolp.note.application.vo.image.ImageSimpleVO;
import com.jacolp.note.application.vo.note.NoteEachSimpleVO;
import com.jacolp.note.application.vo.note.PublicNoteDetailVO;
import com.jacolp.note.application.vo.note.PublicNoteListVO;
import com.jacolp.note.application.vo.note.NoteVO;

@Service
public class PublicNoteServiceImpl implements PublicNoteService {

    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;
    @Autowired private NoteConvertService noteConvertService;

    @Override
    @GuestCacheable(
            cacheName = GuestCacheConstant.GUEST_NOTE_LIST_CACHE,
            ttlSeconds = GuestCacheConstant.GUEST_NOTE_LIST_TTL_SECONDS)
    public PageResult listPublishedNotes(PublicNoteQueryDTO dto) {
        PublicNoteQueryDTO query = normalizeQuery(dto);

        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<NoteVO> records = noteMapper.listPublicPublished(query.getTopicId(), query.getKeyword());
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);

        List<PublicNoteListVO> list = pageInfo.getList().stream()
                .map(this::toListVO)
                .toList();
        return new PageResult(pageInfo.getTotal(), list);
    }

    @Override
    @GuestCacheable(
            cacheName = GuestCacheConstant.GUEST_NOTE_DETAIL_CACHE,
            ttlSeconds = GuestCacheConstant.GUEST_NOTE_DETAIL_TTL_SECONDS)
    public PublicNoteDetailVO getPublishedNoteDetail(Long noteId) {
        if (noteId == null || noteId <= 0) {
            throw new BaseException(NoteConstant.NOTE_ID_INVALID);
        }

        NoteVO note = noteMapper.selectPublicPublishedVoById(noteId);
        if (note == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        PublicNoteDetailVO vo = new PublicNoteDetailVO();
        BeanUtils.copyProperties(note, vo);
        vo.setTags(noteTagMappingMapper.selectPublicTagNamesByNoteId(noteId));
        vo.setImages(noteImageMappingMapper.selectPublicImagesByNoteId(noteId).stream()
                .map(source -> copy(source, new ImageSimpleVO()))
                .toList());
        vo.setEachNotes(noteEachMappingMapper.selectPublicEachNotesBySourceNoteId(noteId).stream()
                .map(source -> copy(source, new NoteEachSimpleVO()))
                .toList());
        vo.setConverted(noteConvertService.getPublishedNoteConvert(noteId));
        return vo;
    }

    private PublicNoteListVO toListVO(NoteVO note) {
        PublicNoteListVO vo = new PublicNoteListVO();
        BeanUtils.copyProperties(note, vo);
        vo.setTags(noteTagMappingMapper.selectPublicTagNamesByNoteId(note.getId()));
        return vo;
    }

    private static <T> T copy(Object source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private PublicNoteQueryDTO normalizeQuery(PublicNoteQueryDTO dto) {
        PublicNoteQueryDTO query = dto == null ? new PublicNoteQueryDTO() : dto;
        if (!StringUtils.hasText(query.getKeyword())) {
            query.setKeyword(null);
        } else {
            query.setKeyword(query.getKeyword().trim());
        }
        return query;
    }
}
