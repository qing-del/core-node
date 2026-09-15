package com.jacolp.module.note.biz.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jacolp.module.note.biz.application.annotation.CheckMissingInfo;
import com.jacolp.context.BindEachRowContext;
import com.jacolp.module.note.biz.application.dto.note.NoteMissingInfoDTO;
import com.jacolp.module.note.biz.application.vo.note.NoteBacklinkVO;
import com.jacolp.module.note.biz.application.vo.note.NoteCheckBindingVO;
import com.jacolp.module.note.biz.application.vo.note.NoteEachMappingRowVO;
import com.jacolp.module.note.biz.application.vo.note.NoteImageMappingRowVO;
import com.jacolp.module.note.biz.application.vo.note.NoteRelationDetailVO;
import com.jacolp.module.note.biz.application.vo.note.NoteSimpleVO;
import com.jacolp.module.note.biz.application.vo.note.NoteTagMappingRowVO;
import com.jacolp.module.note.biz.application.vo.note.ImageBacklinkVO;
import com.jacolp.module.note.biz.application.vo.note.TagBacklinkVO;
import com.jacolp.module.note.biz.application.dto.tag.TagMappingBindDTO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteEachMappingDO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteImageMappingDO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteTagMappingDO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TagDO;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.enums.NoteMissingInfoMask;
import com.jacolp.enums.NoteStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteEachMappingMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteTagMappingMapper;
import com.jacolp.module.note.biz.application.dto.image.ImageMappingBindDTO;
import com.jacolp.module.note.biz.application.dto.note.EachMappingBindDTO;
import com.jacolp.module.media.api.model.MediaFileSummary;
import com.jacolp.module.media.api.model.MediaReviewStatus;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteDO;

import lombok.extern.slf4j.Slf4j;

/**
 * 笔记关联关系管理服务实现。
 * <p>负责笔记与标签、图片、内联笔记三类关联映射的绑定/解绑/校验。
 * 从 {@code NoteServiceImpl} 中解耦抽取，降低原类复杂度。</p>
 *
 * <h3>权限模型</h3>
 * <p>本服务所有公开方法均执行 <b>所有权校验</b>：调用方需提供当前登录用户的
 * JWT 身份（通过 {@link BaseContext#getCurrentId()}），方法内部通过
 *
 * <h3>缺失信息管理</h3>
 * <p>每次绑定/解绑操作后，统一通过 Note 模块的缺失信息切面处理更新，
 * 重算笔记的 {@code missingInfoMask} 和 {@code missingCount}，
 * 并在信息齐全时自动将状态从 {@code PENDING_INFO} 推进到 {@code READY_TO_CONVERT}。</p>
 *
 * <h3>自动补绑定</h3>
 * <p>{@link #checkRelationCompletion(NoteDO)} 方法触发三类映射的自动补绑定
 * 标签/图片/笔记，减少用户手工绑定操作。</p>
 *
 * @see NoteRelationService
 */
@Service
@Slf4j
public class NoteRelationServiceImpl implements NoteRelationService {

    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;

    /**
     * 构建笔记关系详情
     * <p>- 使用 批量查询 和 Map 缓存避免了 O(n) 网络IO 的开销</p>
     * @return 笔记关系详情
     */
    @Override
    public NoteRelationDetailVO getRelationInfo(
            Long noteId,
            List<NoteTagMappingDO> tagMappings, Map<Long, TagDO> tagMap,
            List<NoteImageMappingDO> imageMappings, Map<Long, MediaFileSummary> imageMap,
            List<NoteEachMappingDO> eachMappings, Map<Long, NoteDO> targetNoteMap) {
        // 组装返回 VO
        NoteRelationDetailVO vo = new NoteRelationDetailVO();
        vo.setNoteId(noteId);
        vo.setTags(buildTagRows(tagMappings, tagMap));
        vo.setImages(buildImageRows(imageMappings, imageMap));
        vo.setEachNotes(buildEachRows(eachMappings, targetNoteMap));
        return vo;
    }

    /**
     * 绑定标签映射。
     * <p>校验链：参数完整性 → 映射行归属 → 目标标签存在性 →
     * 名称一致性 → 标签审核状态 → 执行绑定 → 刷新缺失信息。</p>
     */
    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteTagMappingDO bindTagMapping(TagMappingBindDTO dto, TagDO targetTag) {
        // 1) 基础参数校验
        if (dto == null) {
            throw new BaseException("映射ID和标签ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射行归属与目标标签存在性
        NoteTagMappingDO mapping = requireOwnedTagMapping(dto.getMappingId(), userId);

        if (!mapping.getParsedTagName().equals(targetTag.getTagName())) {
            throw new BaseException("标签名称与映射行解析名称不一致，无法绑定");
        }
        // 不是自己的标签，且目标标签未通过审核
        if (!BaseContext.getCurrentId().equals(targetTag.getUserId()) &&
                !AuditStatus.APPROVED.getCode().equals(targetTag.getAuditStatus())) {
            throw new BaseException("目标标签未通过审核，无法绑定");
        }

        noteTagMappingMapper.bindTagById(mapping.getId(), targetTag.getId(), targetTag.getAuditStatus());
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteTagMappingDO unbindTagMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteTagMappingDO mapping = requireOwnedTagMapping(mappingId, userId);
        noteTagMappingMapper.unbindTagById(mappingId);
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteImageMappingDO bindImageMapping(ImageMappingBindDTO dto, MediaFileSummary targetImage) {
        if (dto == null) {
            throw new BaseException("映射ID和图片ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        NoteImageMappingDO mapping = requireOwnedImageMapping(dto.getMappingId(), userId);

        // 不是自己的图片，且图片未通过审核
        if (!userId.equals(targetImage.userId())) {
            if (targetImage.status() != MediaReviewStatus.APPROVED) {
                throw new BaseException("目标图片未通过审核，无法绑定");
            }
        }

        Short isCrossUser = targetImage.userId() != null && !targetImage.userId().equals(mapping.getNoteUserId())
                ? NoteConstant.IS_CROSS_USER
                : NoteConstant.NOT_IS_CROSS_USER;

        noteImageMappingMapper.bindImageById(mapping.getId(), targetImage.id(),
                targetImage.userId(), isCrossUser, mediaStatusCode(targetImage));
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteImageMappingDO unbindImageMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteImageMappingDO mapping = requireOwnedImageMapping(mappingId, userId);
        noteImageMappingMapper.unbindImageById(mappingId);
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteEachMappingDO bindEachMapping(EachMappingBindDTO dto, NoteDO targetNote) {
        if (dto == null) {
            throw new BaseException("映射ID和笔记ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        NoteEachMappingDO mapping = requireOwnedEachMapping(dto.getMappingId(), userId);

        if (!mapping.getParsedNoteName().equals(targetNote.getTitle())) {
            throw new BaseException("笔记标题与映射行解析名称不一致，无法绑定");
        }

        // 如果笔记不属于建立绑定的人 需要笔记审核状态
        NoteStatus targetStatus = NoteStatus.fromCode(targetNote.getStatus());
        if (!BaseContext.getCurrentId().equals(targetNote.getUserId())
                && (!targetStatus.isApproved() && !targetStatus.isPublished())) {
            throw new BaseException("目标笔记未通过审核，无法绑定");
        }

        int affected = noteEachMappingMapper.bindNoteBySourceIdAndParseName(mapping.getSourceNoteId(), mapping.getParsedNoteName(), targetNote.getId(),
                NoteStatus.isPassed(NoteStatus.fromCode(targetNote.getStatus())) ? AuditConstant.PASS : AuditConstant.WAIT);
        BindEachRowContext.setAffectedRows(affected);

        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteEachMappingDO unbindEachMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteEachMappingDO mapping = requireOwnedEachMapping(mappingId, userId);
        noteEachMappingMapper.unbindNoteById(mappingId);
        return mapping;
    }

    /**
     * 校验关联完整性，计算缺失信息。
     * <p>状态流转：NEW → PENDING_INFO → (信息齐全时) READY_TO_CONVERT。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteCheckBindingVO checkRelationCompletion(NoteDO note) {
        NoteCheckBindingVO result = new NoteCheckBindingVO();
        result.setNoteId(note.getId());

        // 基于当前映射状态计算缺失掩码和数量
        NoteMissingInfoDTO missingInfo = scanMissingInfo(note.getId());
        int missingMask = missingInfo.getMissingMask();
        int missingCount = missingInfo.getMissingCount();

        // 如果标签缺失
        if (NoteMissingInfoMask.isTagMissing(missingMask)) {
            result.setMissingTags(getMissingTagNames(note.getId()));
        }

        // 如果图片缺失
        if (NoteMissingInfoMask.isImageMissing(missingMask)) {
            result.setMissingImages(getMissingImageNames(note.getId()));
        }

        // 如果笔记缺失
        if (NoteMissingInfoMask.isNoteMissing(missingMask)) {
            result.setMissingNoteNames(getMissingEachNoteNames(note.getId()));
        }

        boolean isComplete = missingCount == 0;

        // 组装数据
        result.setComplete(isComplete);
        result.setMissingInfoMask(missingMask);
        result.setMissingCount(missingCount);

        return result;
    }

    @Override
    public List<NoteTagMappingDO> listTagMappingsByNoteId(Long noteId) {
        List<NoteTagMappingDO> tagMappingEntities = noteTagMappingMapper.selectByNoteId(noteId);
        if (tagMappingEntities == null || tagMappingEntities.isEmpty()) {
            return List.of();
        }
        return tagMappingEntities;
    }

    @Override
    public List<NoteImageMappingDO> listImageMappingsByNoteId(Long noteId) {
        List<NoteImageMappingDO> imageMappingEntities = noteImageMappingMapper.selectByNoteId(noteId);
        if (imageMappingEntities == null || imageMappingEntities.isEmpty()) {
            return List.of();
        }
        return imageMappingEntities;
    }

    @Override
    public List<NoteEachMappingDO> listEachMappingsByNoteId(Long noteId) {
        List<NoteEachMappingDO> eachMappingEntities = noteEachMappingMapper.selectBySourceNoteId(noteId);
        if (eachMappingEntities == null || eachMappingEntities.isEmpty()) {
            return List.of();
        }
        return eachMappingEntities;
    }

    @Override
    public int batchInsertTagMappings(List<NoteTagMappingDO> mappings) {
        return noteTagMappingMapper.batchInsertMappings(mappings);
    }

    @Override
    public int unbindTagMappingById(Long tagId) {
        return noteTagMappingMapper.unbindTagById(tagId);
    }

    /**
     * 尝试批量绑定标签。
     * <p>如果标签已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的标签映射行
     * @param tagMap 可供选择的标签
     * @throws BaseException 批量绑定失败
     */
    @Override
    public void tryBatchBindTagMappings(List<NoteTagMappingDO> mappings, Map<String, TagDO> tagMap) {
        // 待绑定的标签映射行不能为空
        if (mappings == null || mappings.isEmpty() ||
                tagMap == null || tagMap.isEmpty()) {
            return;
        }

        Long userId = BaseContext.getCurrentId();
        List<NoteTagMappingDO> toBind = new ArrayList<>();

        // 遍历待绑定的标签映射行 检查是否有匹配的标签
        for (NoteTagMappingDO mapping : mappings) {
            TagDO target = tagMap.get(mapping.getParsedTagName());
            if (target == null ||
                    (!userId.equals(target.getUserId()) && !AuditStatus.APPROVED.getCode().equals(target.getAuditStatus()))) {
                continue;
            }

            NoteTagMappingDO bind = new NoteTagMappingDO();
            bind.setId(mapping.getId());
            bind.setTagId(target.getId());
            bind.setStatus(target.getAuditStatus());
            toBind.add(bind);
        }

        // 批量绑定
        if (!toBind.isEmpty()) {
            int affected = noteTagMappingMapper.batchBindTagByIds(toBind);
            if (affected < toBind.size()) {
                throw new BaseException("部分标签绑定失败");
            }
        }
    }

    @Override
    public void tryBatchBindImageMappings(List<NoteImageMappingDO> mappings, Map<String, MediaFileSummary> imageMap) {
        List<NoteImageMappingDO> toBind = new ArrayList<>();
        for (NoteImageMappingDO mapping : mappings) {
            MediaFileSummary target = imageMap.get(mapping.getParsedImageName());
            if (target == null) {
                continue;
            }

            boolean isCrossUser = target.userId() != null && !target.userId().equals(mapping.getNoteUserId());
            if (isCrossUser && target.status() != MediaReviewStatus.APPROVED) {
                continue;
            }

            Short crossUserFlag = isCrossUser ? NoteConstant.IS_CROSS_USER : NoteConstant.NOT_IS_CROSS_USER;
            boolean alreadyBound = Objects.equals(mapping.getImageId(), target.id())
                    && Objects.equals(mapping.getImageUserId(), target.userId())
                    && Objects.equals(mapping.getIsCrossUser(), crossUserFlag)
                    && Objects.equals(mapping.getStatus(), mediaStatusCode(target));
            if (alreadyBound) {
                continue;
            }

            NoteImageMappingDO bind = new NoteImageMappingDO();
            bind.setId(mapping.getId());
            bind.setImageId(target.id());
            bind.setImageUserId(target.userId());
            bind.setIsCrossUser(crossUserFlag);
            bind.setStatus(mediaStatusCode(target));
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            int affected = noteImageMappingMapper.batchBindImageByIds(toBind);
            if (affected < toBind.size()) {
                throw new BaseException("部分图片绑定失败");
            }
        }
    }

    @Override
    public void tryBatchBindNoteMappings(List<NoteEachMappingDO> mappings, Map<String, NoteDO> noteMap) {
        List<NoteEachMappingDO> toBind = new ArrayList<>();
        for (NoteEachMappingDO mapping : mappings) {
            NoteDO target = noteMap.get(mapping.getParsedNoteName());
            if (target == null) {
                continue;
            }
            NoteStatus targetNoteStatus = NoteStatus.fromCode(target.getStatus());
            if (!NoteStatus.PUBLISHED.equals(targetNoteStatus)
            && (target.getUserId() != null && !target.getUserId().equals(BaseContext.getCurrentId()))) {
                continue;
            }

            NoteEachMappingDO bind = new NoteEachMappingDO();
            bind.setId(mapping.getId());
            bind.setTargetNoteId(target.getId());
            bind.setStatus(NoteStatus.isPassed(targetNoteStatus) ? AuditConstant.PASS : AuditConstant.WAIT);
            bind.setTargetNoteId(target.getId());
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            int affected = noteEachMappingMapper.batchBindNoteByIds(toBind);
            if (toBind.size() != affected)  {
                throw new BaseException("部分笔记绑定失败");
            }
        }
    }


    @Override
    public void updateTagMappingPassByTagIds(List<Long> tagIds, Short status) {
        noteTagMappingMapper.updateByTagIds(tagIds, status);
    }

    @Override
    public void updateImageMappingPassByImageIds(List<Long> imageIds, Short status) {
        noteImageMappingMapper.updateByImageIds(imageIds, status);
    }

    @Override
    public void updateEachMappingPassBySourceNoteIds(List<Long> sourceNoteIds, Short status) {
        noteEachMappingMapper.updateBySourceNoteIds(sourceNoteIds, status);
    }

    @Override
    public int initTagBatchInsertMappings(Long noteId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        List<String> distinctTags = tags.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctTags.isEmpty()) {
            return 0;
        }
        List<NoteTagMappingDO> mappings = new ArrayList<>();
        for (String tagName : distinctTags) {
            NoteTagMappingDO mapping = new NoteTagMappingDO();
            mapping.setNoteId(noteId);
            mapping.setTagId(null);
            mapping.setParsedTagName(tagName);
            mapping.setStatus(AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(java.time.LocalDateTime.now());
            mappings.add(mapping);
        }
        return batchInsertTagMappings(mappings);
    }

    @Override
    public int initImageBatchInsertMappings(NoteDO note, List<String> images) {
        if (images == null || images.isEmpty()) {
            return 0;
        }
        List<String> distinctImages = images.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctImages.isEmpty()) {
            return 0;
        }
        List<NoteImageMappingDO> mappings = new ArrayList<>();
        for (String imageName : distinctImages) {
            NoteImageMappingDO mapping = new NoteImageMappingDO();
            mapping.setNoteId(note.getId());
            mapping.setNoteTitle(note.getTitle());
            mapping.setNoteUserId(note.getUserId());
            mapping.setParsedImageName(imageName);
            mapping.setImageId(null);
            mapping.setIsCrossUser(NoteConstant.NOT_IS_CROSS_USER); // 默认先设置为没有跨用户
            mapping.setStatus(AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(java.time.LocalDateTime.now());
            mappings.add(mapping);
        }
        noteImageMappingMapper.batchInsertMappings(mappings);
        return mappings.size();
    }

    @Override
    public int initNoteBatchInsertMappings(Long noteId, List<String> noteReflection) {
        if (noteReflection == null || noteReflection.isEmpty()) {
            return 0;
        }
        List<String> distinctTitles = noteReflection.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctTitles.isEmpty()) {
            return 0;
        }
        List<NoteEachMappingDO> mappings = new ArrayList<>();
        for (String noteName : distinctTitles) {
            // 解析映射信息
            ParseReflection reflection = getParseReflection(noteName);

            NoteEachMappingDO mapping = new NoteEachMappingDO();
            mapping.setSourceNoteId(noteId);
            mapping.setTargetNoteId(null);
            mapping.setParsedNoteName(reflection.parseName());
            mapping.setAnchor(reflection.anchor());
            mapping.setNickname(reflection.nickname());
            mapping.setStatus(AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(java.time.LocalDateTime.now());
            mappings.add(mapping);
        }
        noteEachMappingMapper.batchInsertMappings(mappings);
        return mappings.size();
    }

    @Override
    public boolean isRelated(Long id, Short type) {
        return isRelatedAll(List.of(id), type);
    }

    @Override
    public boolean isRelatedAll(List<Long> ids, Short type) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // type: 1-专题, 2-标签, 3-图片, 4-笔记
        // TODO 全局的默认策略就是被引用了不得被删除(*)
        return false;
    }

    @Override
    public NoteMissingInfoDTO computeNoteMissingInfo(Long noteId) {
        return scanMissingInfo(noteId);
    }

    @Override
    public boolean allMappingsPassed(Long noteId) {
        return noteTagMappingMapper.countByNoteIdAndStatus(noteId, AuditStatus.APPROVED.getCode())
                == noteTagMappingMapper.countByNoteIdAndStatus(noteId, null)
                && noteImageMappingMapper.countByNoteIdAndStatus(noteId, AuditStatus.APPROVED.getCode())
                == noteImageMappingMapper.countByNoteIdAndStatus(noteId, null)
                && noteEachMappingMapper.countByNoteIdAndStatus(noteId, AuditConstant.PASS)
                == noteEachMappingMapper.countByNoteIdAndStatus(noteId, null);
    }

    @Override
    public List<NoteSimpleVO> listNoteSimplesByImageId(Long imageId) {
        return noteImageMappingMapper.selectNoteSimpleByImageId(imageId).stream()
                .map(source -> copy(source, new NoteSimpleVO())).toList();
    }

    @Override
    public List<NoteBacklinkVO> listBacklinksByNoteId(Long noteId, Long userId) {
        return noteEachMappingMapper.selectBacklinksByTargetNoteId(noteId, userId).stream()
                .map(source -> copy(source, new NoteBacklinkVO())).toList();
    }

    @Override
    public List<TagBacklinkVO> listBacklinksByTagId(Long tagId, Long userId) {
        return noteTagMappingMapper.selectBacklinksByTagId(tagId, userId).stream()
                .map(source -> copy(source, new TagBacklinkVO())).toList();
    }

    @Override
    public List<ImageBacklinkVO> listBacklinksByImageId(Long imageId, Long userId) {
        return noteImageMappingMapper.selectBacklinksByImageId(imageId, userId).stream()
                .map(source -> copy(source, new ImageBacklinkVO())).toList();
    }

    @Override
    public int countByImageId(Long imageId) {
        return noteImageMappingMapper.countByImageId(imageId);
    }

    @Override
    public long countRelationByTagId(Long tagId) {
        return noteTagMappingMapper.countByTagId(tagId);
    }

    @Override
    public boolean isMissingTags(Long noteId) {
        return noteTagMappingMapper.existNullTargetTag(noteId) > 1;
    }

    @Override
    public boolean isMissingImages(Long noteId) {
        return false;
    }

    @Override
    public boolean isMissingNotes(Long noteId) {
        return false;
    }

    @Override
    public void deleteByNoteIds(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return;
        }
        noteTagMappingMapper.softDeleteByNoteIds(noteIds);
        noteImageMappingMapper.softDeleteByNoteIds(noteIds);
        noteEachMappingMapper.softDeleteBySourceNoteIds(noteIds);
    }

    @Override
    public void hardDeleteByNoteIds(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return;
        }
        noteTagMappingMapper.hardDeleteByNoteIds(noteIds);
        noteImageMappingMapper.hardDeleteByNoteIds(noteIds);
        noteEachMappingMapper.hardDeleteBySourceNoteIds(noteIds);
    }


    // ===== 私有方法 =====
    private List<NoteTagMappingRowVO> buildTagRows(List<NoteTagMappingDO> mappings, Map<Long, TagDO> tagMap) {
        return mappings.stream().map(mapping -> {
            TagDO tag = mapping.getTagId() == null ? null : tagMap.get(mapping.getTagId());
            boolean validBind = mapping.getTagId() != null
                    && tag != null
                    && Objects.equals(mapping.getParsedTagName(), tag.getTagName());

            NoteTagMappingRowVO row = new NoteTagMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setNoteId(mapping.getNoteId());
            row.setTagId(mapping.getTagId());
            row.setParsedTagName(mapping.getParsedTagName());
            row.setTagName(tag == null ? null : tag.getTagName());
            row.setStatus(mapping.getStatus());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    private List<NoteImageMappingRowVO> buildImageRows(List<NoteImageMappingDO> mappings, Map<Long, MediaFileSummary> imageMap) {
        return mappings.stream().map(mapping -> {
            MediaFileSummary image = mapping.getImageId() == null ? null : imageMap.get(mapping.getImageId());
            boolean validBind = mapping.getImageId() != null
                    && image != null
                    && Objects.equals(mapping.getParsedImageName(), image.filename());

            NoteImageMappingRowVO row = new NoteImageMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setNoteId(mapping.getNoteId());
            row.setImageId(mapping.getImageId());
            row.setParsedImageName(mapping.getParsedImageName());
            row.setFilename(image == null ? null : image.filename());
            row.setIsCrossUser(mapping.getIsCrossUser());
            row.setStatus(mapping.getStatus());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    private static Short mediaStatusCode(MediaFileSummary media) {
        return switch (media.status()) {
            case WAITING -> 0;
            case REVIEWING -> 1;
            case APPROVED -> 2;
            case REJECTED -> 3;
            case DELETED -> 4;
        };
    }

    private List<NoteEachMappingRowVO> buildEachRows(List<NoteEachMappingDO> mappings, Map<Long, NoteDO> noteMap) {
        return mappings.stream().map(mapping -> {
            NoteDO target = mapping.getTargetNoteId() == null ? null : noteMap.get(mapping.getTargetNoteId());
            boolean validBind = mapping.getTargetNoteId() != null
                    && target != null
                    && !NoteStatus.fromCode(target.getStatus()).isDeleted()
                    && Objects.equals(mapping.getParsedNoteName(), target.getTitle());

            NoteEachMappingRowVO row = new NoteEachMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setSourceNoteId(mapping.getSourceNoteId());
            row.setTargetNoteId(mapping.getTargetNoteId());
            row.setParsedNoteName(mapping.getParsedNoteName());
            row.setTargetNoteTitle(target == null ? null : target.getTitle());
            row.setAnchor(mapping.getAnchor());
            row.setNickname(mapping.getNickname());
            row.setStatus(mapping.getStatus());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    /**
     * 获取笔记的标签映射行
     * <p>- 使用 join 联查判别 userId 是否有归属权</p>
     * @param mappingId 笔记ID
     * @return 标签映射行列表
     * @throws BaseException 笔记映射行不存在 / 没有笔记所属权
     */
    private NoteTagMappingDO requireOwnedTagMapping(Long mappingId, Long userId) {
        NoteTagMappingDO mapping;
        if (PermissionContext.isAdmin()) {
            mapping = noteTagMappingMapper.selectById(mappingId);
        } else {
            mapping = noteTagMappingMapper.selectByIdWithValidUserId(mappingId, userId);
        }

        if (mapping == null) {
            throw new BaseException("标签映射行不存在");
        }
        return mapping;
    }

    /**
     * 获取笔记的图片映射行并校验归属权
     * <p>- 使用 note_user_id 列联查判别归属权</p>
     */
    private NoteImageMappingDO requireOwnedImageMapping(Long mappingId, Long userId) {
        NoteImageMappingDO mapping;
        if (PermissionContext.isAdmin()) {
            mapping = noteImageMappingMapper.selectById(mappingId);
        } else {
            mapping = noteImageMappingMapper.selectByIdWithValidUserId(mappingId, userId);
        }
        if (mapping == null) {
            throw new BaseException("图片映射行不存在");
        }
        return mapping;
    }

    /**
     * 获取笔记的内联笔记映射行并校验归属权
     * <p>- 使用 join 联查 biz_note 表判别归属权</p>
     */
    private NoteEachMappingDO requireOwnedEachMapping(Long mappingId, Long userId) {
        NoteEachMappingDO mapping;
        if (PermissionContext.isAdmin()) {
            mapping = noteEachMappingMapper.selectById(mappingId);
        } else {
            mapping = noteEachMappingMapper.selectByIdWithValidUserId(mappingId, userId);
        }
        if (mapping == null) {
            throw new BaseException("笔记映射行不存在");
        }
        return mapping;
    }

    private NoteMissingInfoDTO scanMissingInfo(Long noteId) {
        int missingMask = 0;
        int missingCount = 0;

        int missingTagCount = noteTagMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingTagCount > 0) {
            missingMask |= NoteConstant.MISSING_TAG;
            missingCount += missingTagCount;
        }

        int missingImageCount = noteImageMappingMapper.countByNoteIdAndImageIdIsNull(noteId);
        if (missingImageCount > 0) {
            missingMask |= NoteConstant.MISSING_IMAGE;
            missingCount += missingImageCount;
        }

        int missingNoteCount = noteEachMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingNoteCount > 0) {
            missingMask |= NoteConstant.MISSING_NOTE;
            missingCount += missingNoteCount;
        }

        return new NoteMissingInfoDTO(missingMask, missingCount);
    }

    private List<String> getMissingTagNames(Long noteId) {
        List<NoteTagMappingDO> mappings = noteTagMappingMapper.selectByNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getTagId() == null)
                .map(NoteTagMappingDO::getParsedTagName)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> getMissingImageNames(Long noteId) {
        List<NoteImageMappingDO> mappings = noteImageMappingMapper.selectByNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getImageId() == null)
                .map(NoteImageMappingDO::getParsedImageName)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> getMissingEachNoteNames(Long noteId) {
        List<NoteEachMappingDO> mappings = noteEachMappingMapper.selectBySourceNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getTargetNoteId() == null)
                .map(NoteEachMappingDO::getParsedNoteName)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 解析信息
     */
    private static <T> T copy(Object source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private record ParseReflection(String parseName, String anchor, String nickname) {
    }

    /**
     * 解析笔记名称
     * <p>- 获取笔记名称的解析名称、锚点、昵称</p>
     * <p>- 解析 {@code noteName#anchor|nickname}</p>
     * @param reflectionInfo 笔记映射信息
     * @return 解析信息
     */
    private static @NonNull ParseReflection getParseReflection(String reflectionInfo) {
        int anchorIndex = reflectionInfo.indexOf("#");
        int nicknameIndex = reflectionInfo.indexOf("|");

        String parseName = null;
        String anchor = null;
        if (anchorIndex > 0) {
            parseName = reflectionInfo.substring(0, anchorIndex).trim();
            anchor = reflectionInfo.substring(anchorIndex + 1, nicknameIndex > 0 ?
                    nicknameIndex : reflectionInfo.length());
        }
        String nickname = null;
        if (nicknameIndex > 0) {
            if (parseName == null) {
                parseName = reflectionInfo.substring(0, nicknameIndex).trim();
            }
            nickname = reflectionInfo.substring(nicknameIndex + 1);
        }

        parseName = parseName == null ? reflectionInfo.trim() : parseName.trim();
        anchor = anchor == null || anchor.isEmpty() ? null : anchor.trim();
        nickname = nickname == null || nickname.isEmpty() ? null : nickname.trim();

        return new ParseReflection(parseName, anchor, nickname);
    }
}

