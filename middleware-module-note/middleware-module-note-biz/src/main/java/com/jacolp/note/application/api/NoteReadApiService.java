package com.jacolp.note.application.api;

import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteImageMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.note.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.note.infrastructure.persistence.mapper.TagMapper;
import com.jacolp.note.infrastructure.persistence.projection.MappingProjections;
import com.jacolp.note.api.NoteReadApi;
import com.jacolp.note.api.model.NoteLifecycleStatus;
import com.jacolp.note.api.model.NoteSummary;
import com.jacolp.note.api.model.NoteMediaReferenceSummary;
import com.jacolp.note.api.model.TagReviewStatus;
import com.jacolp.note.api.model.TagSummary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Note-owned batch reader for cross-module query contracts.
 */
@Component
public class NoteReadApiService implements NoteReadApi {

    private static final short NOTE_DELETED = 8;

    private final NoteMapper noteMapper;
    private final TagMapper tagMapper;
    private final NoteImageMappingMapper noteImageMappingMapper;

    public NoteReadApiService(NoteMapper noteMapper, TagMapper tagMapper,
                              NoteImageMappingMapper noteImageMappingMapper) {
        this.noteMapper = noteMapper;
        this.tagMapper = tagMapper;
        this.noteImageMappingMapper = noteImageMappingMapper;
    }

    @Override
    public Map<Long, NoteSummary> findNoteSummariesByIds(Collection<Long> noteIds) {
        List<Long> ids = normalizeIds(noteIds, "noteIds");
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, NoteSummary> summaries = new LinkedHashMap<>();
        for (NoteDO note : noteMapper.selectByIds(ids)) {
            if (note.getStatus() != null && note.getStatus() != NOTE_DELETED) {
                summaries.put(note.getId(), new NoteSummary(note.getId(), note.getUserId(), note.getTopicId(),
                        note.getTitle(), toNoteStatus(note.getStatus()),
                        note.getMdFileSize() == null ? 0L : note.getMdFileSize()));
            }
        }
        return Map.copyOf(summaries);
    }

    @Override
    public Map<Long, TagSummary> findTagSummariesByIds(Collection<Long> tagIds) {
        List<Long> ids = normalizeIds(tagIds, "tagIds");
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, TagSummary> summaries = new LinkedHashMap<>();
        for (TagDO tag : tagMapper.selectByIds(ids)) {
            summaries.put(tag.getId(), new TagSummary(tag.getId(), tag.getUserId(), tag.getTagName(),
                    toTagStatus(tag.getAuditStatus())));
        }
        return Map.copyOf(summaries);
    }

    @Override
    public Map<Long, Long> countMediaReferencesByMediaIds(Collection<Long> mediaIds) {
        List<Long> ids = normalizeIds(mediaIds, "mediaIds");
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        ids.forEach(id -> counts.put(id, 0L));
        for (MappingProjections.ImageNoteCount count : noteImageMappingMapper.countByImageIds(ids)) {
            if (count.getImageId() != null) {
                counts.put(count.getImageId(), count.getRefCount() == null ? 0L : count.getRefCount().longValue());
            }
        }
        return Map.copyOf(counts);
    }

    @Override
    public Map<Long, List<NoteSummary>> findNoteSummariesByMediaIds(Collection<Long> mediaIds) {
        List<Long> ids = normalizeIds(mediaIds, "mediaIds");
        if (ids.isEmpty()) return Map.of();
        Map<Long, List<NoteSummary>> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, new java.util.ArrayList<>()));
        List<NoteImageMappingDO> mappings = noteImageMappingMapper.selectActiveByImageIds(ids);
        Map<Long, NoteSummary> notes = findNoteSummariesByIds(mappings.stream().map(NoteImageMappingDO::getNoteId).toList());
        for (NoteImageMappingDO mapping : mappings) {
            NoteSummary note = notes.get(mapping.getNoteId());
            if (note != null) result.get(mapping.getImageId()).add(note);
        }
        Map<Long, List<NoteSummary>> immutable = new LinkedHashMap<>();
        result.forEach((mediaId, summaries) -> immutable.put(mediaId, List.copyOf(summaries)));
        return Map.copyOf(immutable);
    }

    @Override
    public Map<Long, List<NoteMediaReferenceSummary>> findNoteMediaReferenceSummariesByMediaIds(Collection<Long> mediaIds) {
        List<Long> ids = normalizeIds(mediaIds, "mediaIds");
        if (ids.isEmpty()) return Map.of();
        Map<Long, List<NoteMediaReferenceSummary>> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, new java.util.ArrayList<>()));
        for (NoteImageMappingDO mapping : noteImageMappingMapper.selectActiveByImageIds(ids)) {
            result.get(mapping.getImageId()).add(new NoteMediaReferenceSummary(
                    mapping.getNoteId(), mapping.getNoteTitle(), mapping.getIsCrossUser(),
                    mapping.getStatus(), mapping.getCreateTime()));
        }
        Map<Long, List<NoteMediaReferenceSummary>> immutable = new LinkedHashMap<>();
        result.forEach((mediaId, summaries) -> immutable.put(mediaId, List.copyOf(summaries)));
        return Map.copyOf(immutable);
    }

    @Override
    public long getUserStorageUsageBytes(Long userId) {
        requirePositive(userId, "userId");
        Long usage = noteMapper.sumNoteFileSizeByUserId(userId);
        return usage == null ? 0L : usage;
    }

    private static List<Long> normalizeIds(Collection<Long> ids, String name) {
        Objects.requireNonNull(ids, name + " must not be null");
        return ids.stream()
                .peek(id -> requirePositive(id, name))
                .distinct()
                .toList();
    }

    private static void requirePositive(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(name + " must contain positive ids only");
        }
    }

    private static NoteLifecycleStatus toNoteStatus(Short status) {
        return switch (status) {
            case 0 -> NoteLifecycleStatus.NEW;
            case 1 -> NoteLifecycleStatus.PENDING_INFO;
            case 2 -> NoteLifecycleStatus.READY_TO_CONVERT;
            case 3 -> NoteLifecycleStatus.CONVERTED;
            case 4 -> NoteLifecycleStatus.PENDING_AUDIT;
            case 5 -> NoteLifecycleStatus.APPROVED;
            case 6 -> NoteLifecycleStatus.PUBLISHED;
            case 7 -> NoteLifecycleStatus.REJECTED;
            case 8 -> NoteLifecycleStatus.DELETED;
            default -> throw new IllegalArgumentException("Unsupported legacy note status: " + status);
        };
    }

    private static TagReviewStatus toTagStatus(Short status) {
        return switch (status) {
            case 0 -> TagReviewStatus.WAITING;
            case 1 -> TagReviewStatus.REVIEWING;
            case 2 -> TagReviewStatus.APPROVED;
            case 3 -> TagReviewStatus.REJECTED;
            case 4 -> TagReviewStatus.DELETED;
            default -> throw new IllegalArgumentException("Unsupported legacy tag status: " + status);
        };
    }
}
