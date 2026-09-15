package com.jacolp.note.application.facade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.core.enums.AuditStatus;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.application.service.NoteCoreService;
import com.jacolp.note.application.service.NoteRelationService;
import com.jacolp.note.application.service.TagService;
import com.jacolp.note.context.BindEachRowContext;
import com.jacolp.note.application.dto.tag.TagMappingBindDTO;
import com.jacolp.note.domain.note.NoteMissingInfo;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.note.constant.TagConstant;
import com.jacolp.constant.ImageConstant;
import com.jacolp.note.enums.NoteMissingInfoMask;
import com.jacolp.note.enums.NoteStatus;
import com.jacolp.note.application.dto.image.ImageMappingBindDTO;
import com.jacolp.note.application.dto.note.EachMappingBindDTO;
import com.jacolp.media.api.MediaFileApi;
import com.jacolp.media.api.command.MediaFileLookupCommand;
import com.jacolp.media.api.model.MediaFileSummary;
import com.jacolp.media.api.model.MediaReviewStatus;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteEachMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteImageMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteTagMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.note.application.vo.image.ImageSimpleVO;
import com.jacolp.note.application.vo.note.ImageBacklinkVO;
import com.jacolp.note.application.vo.note.NoteBacklinkVO;
import com.jacolp.note.application.vo.note.NoteCheckBindingVO;
import com.jacolp.note.application.vo.note.NoteRelationDetailVO;
import com.jacolp.note.application.vo.note.TagBacklinkVO;

@Service
public class NoteRelationFacadeImpl implements NoteRelationFacade {

    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteRelationService noteRelationService;

    @Autowired private TagService tagService;
    @Autowired private MediaFileApi mediaFileApi;

    /**
     * 获取笔记三类关联映射详情（标签 / 图片 / 内联笔记）。
     * <p>通过 {@link NoteCoreService#getById} 校验所有权后委托给 {@link NoteRelationService}。</p>
     */
    @Override
    public NoteRelationDetailVO getRelationInfo(Long noteId) {
        if (!PermissionContext.isAdmin()) {
            noteCoreService.getNoteVOById(noteId);    // 不是管理员才需要校验
        }

        // 先进行三联映射查询
        List<NoteTagMappingDO> tagMappings = noteRelationService.listTagMappingsByNoteId(noteId);
        List<NoteImageMappingDO> imageMappings = noteRelationService.listImageMappingsByNoteId(noteId);
        List<NoteEachMappingDO> eachMappings = noteRelationService.listEachMappingsByNoteId(noteId);

        // 构建缓存
        Map<Long, TagDO> tagMap = buildTagMap(tagMappings);
        Map<Long, MediaFileSummary> imageMap = buildImageMap(imageMappings);
        Map<Long, NoteDO> targetNoteMap = buildTargetNoteMap(eachMappings);

        // 构建返回结果
        return noteRelationService.getRelationInfo(
                noteId,
                tagMappings, tagMap,
                imageMappings, imageMap,
                eachMappings, targetNoteMap);
    }

    /**
     * 获取图片简要列表
     * <p>- 此处没有权限校验</p>
     */
    @Override
    public List<ImageSimpleVO> listImageSimpleVOsByNoteId(Long noteId) {
        List<NoteImageMappingDO> mappings = noteRelationService.listImageMappingsByNoteId(noteId);
        List<Long> imageIds = mappings
                .stream()
                .map(NoteImageMappingDO::getImageId)
                .toList();

        Map<Long, MediaFileSummary> imageMap = imageIds.isEmpty()
                ? Map.of()
                : mediaFileApi.findByIds(imageIds);

        List<ImageSimpleVO> result = new ArrayList<>();
        buildNoteImageSimpleVOList(noteId, mappings, imageMap, result);
        return result;
    }

    /**
     * 绑定标签映射关系
     */
    @Override
    public void bindTagMapping(TagMappingBindDTO dto) {
        TagDO targetTag = tagService.getByIdAndUserId(dto.getTagId(), BaseContext.getCurrentId());
        NoteTagMappingDO mapping = noteRelationService.bindTagMapping(dto, targetTag);

        // 检查是否需要更新笔记状态
        NoteDO note = noteCoreService.getById(mapping.getNoteId());

        // 检查状态是否允许操作
        if (!NoteStatus.canBindOperation(NoteStatus.fromCode(note.getStatus()))) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        NoteMissingInfo missingInfo = new NoteMissingInfo(note.getMissingInfoMask(), note.getMissingCount());
        if (missingInfo.shouldRecalculateAfterBind(1)) {
            tryConvertNoteToReady(note);
        } else {
            NoteMissingInfo updated = missingInfo.afterPartialBind(NoteMissingInfoMask.TAG, 1,
                    noteRelationService.isMissingTags(note.getId()));
            note.setMissingCount(updated.count());
            note.setMissingInfoMask(updated.mask());
        }

        // 更新笔记状态
        noteCoreService.update(note);
    }

    @Override
    public NoteTagMappingDO unbindTagMapping(Long mappingId) {
        // 尝试解除绑定
        NoteTagMappingDO result = noteRelationService.unbindTagMapping(mappingId);

        // 检查是否需要更新笔记状态
        NoteDO note = noteCoreService.getById(result.getNoteId());

        // 检查状态是否允许操作
        if (!NoteStatus.canBindOperation(NoteStatus.fromCode(note.getStatus()))) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        NoteMissingInfo missingInfo = new NoteMissingInfo(note.getMissingInfoMask(), note.getMissingCount());
        Short statusAfterUnbind = missingInfo.statusAfterUnbind(note.getStatus());
        if (!Objects.equals(statusAfterUnbind, note.getStatus())) {
            note.setStatus(statusAfterUnbind);
        }
        NoteMissingInfo updated = missingInfo.afterUnbind(NoteMissingInfoMask.TAG);
        note.setMissingInfoMask(updated.mask());
        note.setMissingCount(updated.count());

        // 更新笔记
        noteCoreService.update(note);

        // 返回结果
        return result;
    }

    /**
     * 绑定图片映射关系
     */
    @Override
    public void bindImageMapping(ImageMappingBindDTO dto) {
        MediaFileSummary targetImage = mediaFileApi.findByIds(List.of(dto.getImageId())).get(dto.getImageId());
        if (targetImage == null) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        NoteImageMappingDO mapping = noteRelationService.bindImageMapping(dto, targetImage);

        // 检查是否需要更新笔记状态
        NoteDO note = noteCoreService.getById(mapping.getNoteId());

        // 检查状态是否允许操作
        if (!NoteStatus.canBindOperation(NoteStatus.fromCode(note.getStatus()))) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        NoteMissingInfo missingInfo = new NoteMissingInfo(note.getMissingInfoMask(), note.getMissingCount());
        if (missingInfo.shouldRecalculateAfterBind(1)) {
            tryConvertNoteToReady(note);
        } else {
            NoteMissingInfo updated = missingInfo.afterPartialBind(NoteMissingInfoMask.IMAGE, 1,
                    noteRelationService.isMissingImages(note.getId()));
            note.setMissingCount(updated.count());
            note.setMissingInfoMask(updated.mask());
        }

        // 更新笔记状态
        noteCoreService.update(note);
    }

    @Override
    public NoteImageMappingDO unbindImageMapping(Long mappingId) {
        // 尝试解除绑定
        NoteImageMappingDO result = noteRelationService.unbindImageMapping(mappingId);

        // 检查是否需要更新笔记状态
        NoteDO note = noteCoreService.getById(result.getNoteId());

        // 检查状态是否允许操作
        if (!NoteStatus.canBindOperation(NoteStatus.fromCode(note.getStatus()))) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        NoteMissingInfo missingInfo = new NoteMissingInfo(note.getMissingInfoMask(), note.getMissingCount());
        Short statusAfterUnbind = missingInfo.statusAfterUnbind(note.getStatus());
        if (!Objects.equals(statusAfterUnbind, note.getStatus())) {
            note.setStatus(statusAfterUnbind);
        }
        NoteMissingInfo updated = missingInfo.afterUnbind(NoteMissingInfoMask.IMAGE);
        note.setMissingInfoMask(updated.mask());
        note.setMissingCount(updated.count());

        // 更新笔记
        noteCoreService.update(note);

        // 返回结果
        return result;
    }

    /**
     * 绑定内联笔记映射关系
     */
    @Override
    public void bindEachMapping(EachMappingBindDTO dto) {
        NoteDO targetNote = noteCoreService.getById(dto.getNoteId());
        NoteEachMappingDO mapping = null;
        int affectedRow = 0;
        try {
            mapping = noteRelationService.bindEachMapping(dto, targetNote);
            affectedRow = BindEachRowContext.getAffectedRows();
        } finally {
            BindEachRowContext.clear();
        }

        // 检查是否需要更新笔记状态
        NoteDO note = noteCoreService.getById(mapping.getNoteId());

        // 检查状态是否允许操作
        if (!NoteStatus.canBindOperation(NoteStatus.fromCode(note.getStatus()))) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        NoteMissingInfo missingInfo = new NoteMissingInfo(note.getMissingInfoMask(), note.getMissingCount());
        if (missingInfo.shouldRecalculateAfterBind(affectedRow)) {
            tryConvertNoteToReady(note);
        } else {
            NoteMissingInfo updated = missingInfo.afterPartialBind(NoteMissingInfoMask.NOTE, affectedRow,
                    noteRelationService.isMissingNotes(note.getId()));
            note.setMissingCount(updated.count());
            note.setMissingInfoMask(updated.mask());
        }

        // 更新笔记状态
        noteCoreService.update(note);
    }

    @Override
    public NoteEachMappingDO unbindEachMapping(Long mappingId) {
        // 尝试解除绑定
        NoteEachMappingDO result = noteRelationService.unbindEachMapping(mappingId);

        // 检查是否需要更新笔记状态
        NoteDO note = noteCoreService.getById(result.getNoteId());

        // 检查状态是否允许操作
        if (!NoteStatus.canBindOperation(NoteStatus.fromCode(note.getStatus()))) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        NoteMissingInfo missingInfo = new NoteMissingInfo(note.getMissingInfoMask(), note.getMissingCount());
        Short statusAfterUnbind = missingInfo.statusAfterUnbind(note.getStatus());
        if (!Objects.equals(statusAfterUnbind, note.getStatus())) {
            note.setStatus(statusAfterUnbind);
        }
        NoteMissingInfo updated = missingInfo.afterUnbind(NoteMissingInfoMask.NOTE);
        note.setMissingInfoMask(updated.mask());
        note.setMissingCount(updated.count());

        // 更新笔记
        noteCoreService.update(note);

        // 返回结果
        return result;
    }

    @Override
    public NoteCheckBindingVO checkRelationCompletion(Long noteId) {
        NoteDO note = noteCoreService.getById(noteId);

        // 先尝试自动补绑定已存在且可绑定的标签/图片/内联笔记
        syncBindableMappings(noteId, note.getUserId(), note.getTopicId());

        return tryConvertNoteToReady(note);
    }

    /**
     * 查询反向引用列表（哪些笔记引用了 noteId）
     * <p>用户端要求目标笔记可见性：拥有者 或 已公开(status=6)</p>
     * <p>管理端跳过可见性校验</p>
     */
    @Override
    public List<NoteBacklinkVO> listBacklinksByNoteId(Long noteId) {
        if (PermissionContext.isAdmin()) {
            return noteRelationService.listBacklinksByNoteId(noteId, null);
        }

        Long currentUserId = BaseContext.getCurrentId();
        NoteDO target = noteCoreService.getEntityById(noteId);

        // 校验可见性
        boolean isOwner = Objects.equals(target.getUserId(), currentUserId);    // 校验归属权
        boolean isPublished = NoteStatus.PUBLISHED.getCode().equals(target.getStatus());    // 校验是否已发布
        if (!isOwner && !isPublished) {
            throw new BaseException(UserConstant.PERMISSION_DENIED);
        }

        return noteRelationService.listBacklinksByNoteId(noteId, currentUserId);
    }

    /**
     * 查询标签反向引用列表（哪些笔记引用了 tagId）
     * <p>用户端要求目标标签可见性：拥有者 或 已通过审核(auditStatus=2)</p>
     * <p>管理端跳过可见性校验</p>
     */
    @Override
    public List<TagBacklinkVO> listBacklinksByTagId(Long tagId) {
        if (PermissionContext.isAdmin()) {
            return noteRelationService.listBacklinksByTagId(tagId, null);
        }

        Long currentUserId = BaseContext.getCurrentId();
        TagDO tag = tagService.getByIds(List.of(tagId)).stream()
                .findFirst().orElse(null);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        boolean isOwner = Objects.equals(tag.getUserId(), currentUserId);
        boolean isApproved = Objects.equals(tag.getAuditStatus(), AuditStatus.APPROVED.getCode());
        if (!isOwner && !isApproved) {
            throw new BaseException(UserConstant.PERMISSION_DENIED);
        }

        return noteRelationService.listBacklinksByTagId(tagId, currentUserId);
    }

    /**
     * 查询图片反向引用列表（哪些笔记引用了 imageId）
     * <p>用户端要求目标图片可见性：拥有者 或 已公开(isPublic=1)</p>
     * <p>管理端跳过可见性校验</p>
     */
    @Override
    public List<ImageBacklinkVO> listBacklinksByImageId(Long imageId) {
        if (PermissionContext.isAdmin()) {
            return noteRelationService.listBacklinksByImageId(imageId, null);
        }

        Long currentUserId = BaseContext.getCurrentId();
        MediaFileSummary image = mediaFileApi.findByIds(List.of(imageId)).get(imageId);
        if (image == null) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }

        boolean isOwner = Objects.equals(image.userId(), currentUserId);
        boolean isPublic = image.publiclyVisible();
        if (!isOwner && !isPublic) {
            throw new BaseException(UserConstant.PERMISSION_DENIED);
        }

        return noteRelationService.listBacklinksByImageId(imageId, currentUserId);
    }

    /**
     * 尝试自动补绑定已存在且可绑定的标签/图片/内联笔记
     * <p>- 此处不会校验权限</p>
     * <p>- 使用{@link NoteRelationService#checkRelationCompletion(NoteDO)} 去检查绑定状态</p>
     * <p>- 使用{@link NoteCoreService#update(NoteDO)} 更新笔记</p>
     */
    private @NonNull NoteCheckBindingVO tryConvertNoteToReady(NoteDO note) {
        // 获取笔记检查需要使用的关联信息
        NoteCheckBindingVO vo = noteRelationService.checkRelationCompletion(note);

        // 检查是否需要更新关联信息
        if (vo.isComplete()) { // 检查是否需要更新笔记状态
            NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
            NoteStatus targetStatus = NoteStatus.fromCode(NoteConstant.STATUS_READY_TO_CONVERT);
            // 检查是否可以进行转换
            if (vo.isComplete() && currentStatus.canTransitionTo(targetStatus)) {
                vo.setStatus(targetStatus.getCode());   // 转换成“可转换”状态
                vo.setStatusDesc(targetStatus.getDesc());
            } else {
                // 判断原来的笔记是不是NEW，是NEW的话发生自动转换状态
                if (NoteStatus.NEW.equals(currentStatus)) {
                    vo.setStatus(NoteStatus.READY_TO_CONVERT.getCode());
                } else {
                    vo.setStatus(currentStatus.getCode());  // 否则保持原状态
                }
                vo.setStatusDesc(currentStatus.getDesc());
            }

        }
        // 组装更新使用的数据
        note.setStatus(vo.getStatus());
        note.setMissingInfoMask(vo.getMissingInfoMask());
        note.setMissingCount(vo.getMissingCount());
        noteCoreService.update(note);
        return vo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建简单图片信息列表
     */
    private static void buildNoteImageSimpleVOList(Long noteId, List<NoteImageMappingDO> mappings,
                                                   Map<Long, MediaFileSummary> imageMap, List<ImageSimpleVO> result) {
        for (NoteImageMappingDO mapping : mappings) {
            ImageSimpleVO vo = new ImageSimpleVO();
            vo.setNoteId(noteId);
            vo.setParsedImageName(mapping.getParsedImageName());
            vo.setIsCrossUser(mapping.getIsCrossUser());
            vo.setImageId(mapping.getImageId());

            if (mapping.getImageId() == null) {
                vo.setIsMissing(NoteConstant.MISSED_INFO);
            } else {
                MediaFileSummary image = imageMap.get(mapping.getImageId());
                if (image != null) {
                    vo.setFilename(image.filename());
                    vo.setOssUrl(image.url());
                    vo.setIsPublic(image.publiclyVisible() ? (short) 1 : (short) 0);
                    vo.setStatus(mediaStatusCode(image.status()));
                    vo.setIsMissing(NoteConstant.NOT_MISSED_INFO);
                } else {
                    vo.setIsMissing(NoteConstant.MISSED_INFO);
                }
            }
            result.add(vo);
        }
    }

    /**
     * 同步可绑定的映射关系
     */
    private void syncBindableMappings(Long noteId, Long userId, Long topicId) {
        // 同步标签映射
        List<NoteTagMappingDO> tagMappings = Optional.ofNullable
                (noteRelationService.listTagMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, TagDO> tagMap = getTagEntitiesMap(tagMappings, userId);
        noteRelationService.tryBatchBindTagMappings(tagMappings, tagMap);

        if (topicId == null) return;

        // 同步图片映射
        List<NoteImageMappingDO> imageMappings = Optional.ofNullable
                (noteRelationService.listImageMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, MediaFileSummary> imageMap = getImageEntitiesMap(imageMappings, userId, topicId);
        noteRelationService.tryBatchBindImageMappings(imageMappings, imageMap);

        // 同步内联笔记映射
        List<NoteEachMappingDO> noteMappings = Optional.ofNullable
                (noteRelationService.listEachMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, NoteDO> noteMap = getNoteEntitiesMap(noteMappings, userId, topicId);
        noteRelationService.tryBatchBindNoteMappings(noteMappings, noteMap);
    }

    /**
     * 获取标签实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link TagService#getByNamesAndUserId(List, Long)} 方法</p>
     *
     * @param mappings 标签映射行
     * @param userId   用户 id
     * @return 标签数据对象的 <tagName, TagDO> 的 Map
     */
    private Map<String, TagDO> getTagEntitiesMap(List<NoteTagMappingDO> mappings, Long userId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings
                .stream()
                .map(NoteTagMappingDO::getParsedTagName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<TagDO> tags = Optional.ofNullable(tagService.getByNamesAndUserId(parsedNames, userId))
                .orElse(List.of());
        if (tags.isEmpty()) {
            return Map.of();
        }

        return tags.stream()
                .collect(Collectors
                        .toMap(
                                TagDO::getTagName,
                                tag -> tag,
                                (left, right) -> left)
                );
    }

    /**
     * 获取图片实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>通过 MediaFileApi 按 owner/topic 和文件名批量读取。</p>
     *
     * @param mappings 图片映射行
     * @param userId   用户 id
     * @param topicId  话题 id
     * @return 图片摘要的 <imageName, MediaFileSummary> 的 Map
     */
    private Map<String, MediaFileSummary> getImageEntitiesMap(List<NoteImageMappingDO> mappings, Long userId, Long topicId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteImageMappingDO::getParsedImageName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        return mediaFileApi.findByOwnerTopicAndFilenames(new MediaFileLookupCommand(userId, topicId, parsedNames));
    }

    /**
     * 获取笔记实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link NoteCoreService#getByUserIdAndTopicIdAndTitles(Long, Long, List)} 方法</p>
     *
     * @param mappings 笔记映射行
     * @param userId   用户 id
     * @param topicId  话题 id
     * @return 笔记实体的 <noteName, NoteDO> 的 Map
     */
    private Map<String, NoteDO> getNoteEntitiesMap(List<NoteEachMappingDO> mappings,
                                                       Long userId, Long topicId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteEachMappingDO::getParsedNoteName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<NoteDO> notes = Optional.ofNullable(
                        noteCoreService.getByUserIdAndTopicIdAndTitles(userId, topicId, parsedNames))
                .orElse(List.of());
        if (notes.isEmpty()) {
            return Map.of();
        }

        return notes.stream()
                .collect(Collectors
                        .toMap(
                                NoteDO::getTitle,
                                note -> note,
                                (left, right) -> left)
                );
    }

    private Map<Long, TagDO> buildTagMap(List<NoteTagMappingDO> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteTagMappingDO::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }
        return tagService.getByIds(ids).stream()
                .collect(Collectors.toMap(TagDO::getId, tag -> tag, (left, right) -> left));
    }

    private Map<Long, MediaFileSummary> buildImageMap(List<NoteImageMappingDO> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteImageMappingDO::getImageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }
        return mediaFileApi.findByIds(ids);
    }

    private static Short mediaStatusCode(MediaReviewStatus status) {
        return switch (status) { case WAITING -> 0; case REVIEWING -> 1; case APPROVED -> 2; case REJECTED -> 3; case DELETED -> 4; };
    }

    private Map<Long, NoteDO> buildTargetNoteMap(List<NoteEachMappingDO> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteEachMappingDO::getTargetNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return noteCoreService.getByIds(ids).stream()
                .collect(Collectors.toMap(NoteDO::getId, note -> note, (left, right) -> left));
    }
}

