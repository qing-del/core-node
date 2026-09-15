package com.jacolp.note.application.service;

import java.util.List;

import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.core.json.JacksonObjectMapper;
import com.jacolp.note.context.NoteImageResolveContext;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteContextDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteConvertedDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.mapper.NoteConvertMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.constant.NoteConstant;
import com.jacolp.framework.markdown.converter.MarkdownHtmlEngine;
import com.jacolp.framework.markdown.converter.MarkdownHtmlEngine.FrontMatter;
import com.jacolp.framework.markdown.converter.MarkdownHtmlEngine.HtmlProcessResult;
import com.jacolp.note.application.vo.note.NoteConvertMetaVO;
import com.jacolp.note.application.vo.note.NoteConvertResultVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NoteConvertServiceImpl implements NoteConvertService {

    private static final ObjectMapper OBJECT_MAPPER = new JacksonObjectMapper();

    @Autowired private NoteConvertMapper noteConvertMapper;
    @Autowired private MarkdownHtmlEngine markdownHtmlEngine;

    @Override
    public String convertAndSave(NoteDO note, NoteContextDO context) {
        NoteImageResolveContext.setCurrentNoteId(note.getId());
        try {
            HtmlProcessResult result = markdownHtmlEngine.process(context.getMarkdownContent());
            FrontMatter meta = result.meta().withFallbackTitle(note.getTitle());
            NoteConvertedDO converted = buildNoteConvertEntity(note.getId(), meta, result);
            if (noteConvertMapper.upsertConverted(converted) < 1) {
                log.error("Note convert failed, noteId: {}", note.getId());
                throw new BaseException(NoteConstant.NOTE_CONVERT_FAILED);
            }
            return meta.title();
        } finally {
            NoteImageResolveContext.clear();
        }
    }

    @Override
    public void delete(Long noteId) {
        if (noteConvertMapper.deleteByNoteId(noteId) < 1) {
            log.error("Note convert delete failed, noteId: {}", noteId);
            throw new BaseException(NoteConstant.NOTE_DELETE_FAILED);
        }
    }

    @Override
    public void deleteAllByNoteIds(List<Long> noteIds) {
        if (noteIds != null && !noteIds.isEmpty()) {
            noteConvertMapper.deleteByNoteIds(noteIds);
        }
    }

    @Override
    public NoteConvertResultVO getNoteConvert(Long noteId) {
        NoteConvertedDO converted = PermissionContext.isAdmin()
                ? noteConvertMapper.selectByNoteIdWithValidUserId(noteId, null)
                : noteConvertMapper.selectByNoteIdWithValidUserId(noteId, BaseContext.getCurrentId());
        if (converted == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }
        return toConvertResultVO(converted);
    }

    @Override
    public NoteConvertResultVO getPublishedNoteConvert(Long noteId) {
        NoteConvertedDO converted = noteConvertMapper.selectPublishedByNoteId(noteId);
        if (converted == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }
        return toConvertResultVO(converted);
    }

    private NoteConvertResultVO toConvertResultVO(NoteConvertedDO entity) {
        NoteConvertMetaVO metaVO = new NoteConvertMetaVO();
        metaVO.setTitle(entity.getTitle());
        metaVO.setCreateTime(entity.getCreateTimeStr());
        metaVO.setTags(readTags(entity.getTagsJson()));
        NoteConvertResultVO resultVO = new NoteConvertResultVO();
        resultVO.setMeta(metaVO);
        resultVO.setTocHtml(entity.getTocHtml());
        resultVO.setBodyHtml(entity.getBodyHtml());
        return resultVO;
    }

    private @NonNull NoteConvertedDO buildNoteConvertEntity(Long noteId, FrontMatter meta, HtmlProcessResult result) {
        NoteConvertedDO converted = new NoteConvertedDO();
        converted.setNoteId(noteId);
        converted.setTitle(meta.title());
        converted.setTagsJson(writeTags(meta.tags()));
        converted.setCreateTimeStr(meta.createTime());
        converted.setTocHtml(result.tocHtml());
        converted.setBodyHtml(result.bodyHtml());
        return converted;
    }

    private static List<String> readTags(String json) {
        try {
            List<String> tags = OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return tags == null ? List.of() : tags;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String writeTags(List<String> tags) {
        try {
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
