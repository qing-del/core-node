package com.jacolp.module.note.biz.application.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jacolp.context.PermissionContext;
import com.jacolp.module.audit.api.AuditApplicationApi;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.module.audit.api.PendingAuditApplicationQuery;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.ScopeConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.TagConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TagMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.dto.TagNoteCountDTO;
import com.jacolp.module.note.biz.application.dto.tag.TagAddDTO;
import com.jacolp.module.note.biz.application.dto.tag.TagBatchAddDTO;
import com.jacolp.module.note.biz.application.dto.tag.TagModifyDTO;
import com.jacolp.module.note.biz.application.dto.tag.TagQueryDTO;
import com.jacolp.module.note.biz.application.dto.tag.UserTagAssignDTO;
import com.jacolp.module.note.biz.application.dto.tag.UserTagQueryDTO;
import com.jacolp.module.note.biz.application.dto.tag.UserTagRemoveDTO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteTagMappingDO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.module.note.biz.application.vo.tag.TagBatchAddVO;
import com.jacolp.module.note.biz.application.vo.tag.TagStatsVO;
import com.jacolp.module.note.biz.application.vo.tag.TagVO;
import com.jacolp.module.note.biz.application.vo.tag.UserTagSimpleVO;
import com.jacolp.result.PageResult;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TagServiceImpl implements TagService {

    @Autowired private TagMapper tagMapper;

    // 来自其他模块的 Mapper
    @Autowired private AuditApplicationApi auditApplicationApi;
    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteRelationService noteRelationService;

    @Override
    public void addTag(TagAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String tagName = normalizeTagName(dto.getTagName());
        validateTagName(tagName);

        // 检查是否存在同主题下的同名标签
        TagDO existed = tagMapper.selectByUserIdAndTagName(userId, tagName);
        if (existed != null) {
            throw new BaseException(TagConstant.TAG_ALREADY_EXISTS);
        }

        TagDO tag = new TagDO();
        tag.setUserId(userId);
        tag.setTagName(tagName);
        tag.setAuditStatus(AuditStatus.WAIT.getCode());
        int count = tagMapper.insertTag(tag);
        if (count <= 0) {
            throw new BaseException(TagConstant.TAG_ADD_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagBatchAddVO batchAddTags(TagBatchAddDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 去重并保序，同时过滤空白
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tagName : dto.getTagNames()) {
            if (!StringUtils.hasText(tagName)) {
                continue;
            }
            String trimmed = normalizeTagName(tagName);
            validateTagName(trimmed);
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            throw new BaseException(TagConstant.TAG_NAME_REQUIRED);
        }

        List<String> existedNames = tagMapper.selectTagNamesByUserId(userId);
        Set<String> existedSet = new HashSet<>(existedNames);

        List<String> existingTags = new ArrayList<>();
        List<TagDO> toInsert = new ArrayList<>();
        for (String tagName : normalized) {
            if (existedSet.contains(tagName)) {
                existingTags.add(tagName);
            } else {
                TagDO tag = new TagDO();
                tag.setUserId(userId);
                tag.setTagName(tagName);
                tag.setAuditStatus(AuditStatus.WAIT.getCode());
                toInsert.add(tag);
            }
        }

        int successCount = 0;
        if (!toInsert.isEmpty()) {
            successCount = tagMapper.batchInsertTags(toInsert);
        }
        return new TagBatchAddVO(successCount, existingTags);
    }

    @Override
    public void modifyTag(TagModifyDTO dto) {
        Long userId = BaseContext.getCurrentId();
        validateTagId(dto.getId());

        TagDO existed = tagMapper.selectByIdAndUserId(dto.getId(), userId);
        if (existed == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        // 检查标签是否被引用，被引用的状态下无法修改
        if (noteRelationService.countRelationByTagId(dto.getId()) > 0) {
            throw new BaseException(TagConstant.TAG_REFERENCED);
        }

        // 检查是否存在重名的标签
        String tagName = normalizeTagName(dto.getTagName());
        validateTagName(tagName);
        if (!tagName.equals(existed.getTagName())) {
            TagDO duplicate = tagMapper.selectByUserIdAndTagName(userId, tagName);
            if (duplicate != null && !duplicate.getId().equals(dto.getId())) {
                throw new BaseException(TagConstant.TAG_ALREADY_EXISTS);
            }
        }

        TagDO update = new TagDO();
        update.setId(dto.getId());
        update.setUserId(userId);
        update.setTagName(tagName);
        int count = tagMapper.updateTag(update);
        if (count <= 0) {
            throw new BaseException(TagConstant.TAG_UPDATE_FAILED);
        }
    }

    /**
     * 删除标签
     * <p>- 查询待删除的标签列表时，会根据 {@link PermissionContext#isAdmin()} 来判断是否需要开启用户过滤</p>
     * @param ids
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTags(List<Long> ids) {
        // 获取删除列表
        Long userId = PermissionContext.isAdmin() ? null : BaseContext.getCurrentId();
        List<TagNoteCountDTO> checks = tagMapper.selectDeleteChecksByIds(userId, ids);
        if (checks.size() != ids.size()) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        // 检查是否存在引用
        for (TagNoteCountDTO check : checks) {
            if (check.getNoteCount() != null && check.getNoteCount() > 0) {
                throw new BaseException(TagConstant.TAG_DELETE_NOT_ALLOWED_PREFIX
                        + check.getTagName()
                        + TagConstant.TAG_DELETE_NOT_ALLOWED_SUFFIX);
            }
        }
        List<TagDO> tags = tagMapper.selectByIds(new ArrayList<>(ids));
        for (TagDO tag : tags) {
            AuditStatus status = AuditStatus.fromCode(tag.getAuditStatus());
            if (!status.canTransitionTo(AuditStatus.DELETED)) {
                throw new BaseException("审核中的标签不能删除");
            }
        }

        int count = tagMapper.deleteByIds(userId, ids);
        if (count <= 0) {
            throw new BaseException(TagConstant.TAG_DELETE_FAILED);
        }
    }

    @Override
    public PageResult listTags(TagQueryDTO dto) {
        if (dto == null) {
            dto = new TagQueryDTO();
        }

        Long userId = dto.getUserId();
        if (userId != null && userId <= 0) {
            throw new BaseException(UserConstant.NOT_FOUND_USER);
        }

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<TagVO> records = tagMapper.listByCondition(userId, normalizeKeyword(dto.getKeyword()));
        PageInfo<TagVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 用户端条件查询：当前用户自己的标签 + 别人已通过审核的标签。
     */
    @Override
    public PageResult listUserTags(UserTagQueryDTO dto) {
        if (dto == null) {
            dto = new UserTagQueryDTO();
        }
        Long userId = BaseContext.getCurrentId();
        boolean globalScope = ScopeConstant.SCOPE_GLOBAL.equals(dto.getScope());

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());

        List<TagVO> records = tagMapper.listByUserCondition(userId, normalizeKeyword(dto.getKeyword()), globalScope);
        PageInfo<TagVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 用户端发起标签审核申请。
     */
    @Override
    public void submitTagAudit(Long tagId) {
        Long userId = BaseContext.getCurrentId();
        validateTagId(tagId);

        TagDO tag = tagMapper.selectByIdAndUserId(tagId, userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }
        AuditStatus status = AuditStatus.fromCode(tag.getAuditStatus());
        if (!status.canTransitionTo(AuditStatus.AUDITING)) {
            throw new BaseException("该标签已通过审核");
        }
        if (auditApplicationApi.hasPendingApplication(new PendingAuditApplicationQuery(AuditTargetType.TAG, tagId))) {
            throw new BaseException("该标签已有待审核的申请");
        }

        auditApplicationApi.createApplication(new CreateAuditApplicationCommand(
                AuditTargetType.TAG, tagId, userId, null));

        tagMapper.updateAuditStatusByIds(List.of(tagId), AuditStatus.AUDITING.getCode());
    }

    /**
     * 用户端撤销标签审核申请。
     */
    @Override
    public void cancelTagAudit(Long tagId) {
        Long userId = BaseContext.getCurrentId();
        validateTagId(tagId);

        TagDO tag = tagMapper.selectByIdAndUserId(tagId, userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }
        AuditStatus status = AuditStatus.fromCode(tag.getAuditStatus());
        if (!status.canTransitionTo(AuditStatus.WAIT)) {
            throw new BaseException("该标签未处于审核中");
        }

        auditApplicationApi.cancelApplication(new CancelAuditApplicationCommand(
                AuditTargetType.TAG, tagId, userId));
        tagMapper.updateAuditStatusByIds(List.of(tagId), AuditStatus.WAIT.getCode());
    }

    /**
     * 获取当前用户标签统计。
     */
    @Override
    public TagStatsVO getUserTagStats() {
        Long userId = BaseContext.getCurrentId();
        long tagCount = tagMapper.countByUserId(userId);
        long passedCount = tagMapper.countPassedByUserId(userId);
        return new TagStatsVO(tagCount, passedCount);
    }

    @Override
    public TagDO getByIdAndUserId(Long id, Long userId) {
        return tagMapper.selectByIdAndUserId(id, userId);
    }

    @Override
    public List<TagDO> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectByIds(ids);
    }

    @Override
    public List<TagDO> getByNamesAndUserId(List<String> names, Long userId) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectIdsByNamesAndUserId(names, userId);
    }

    // ===== 用户端方法 =====

    @Override
    public List<UserTagSimpleVO> listUserTagSimples() {
        Long userId = BaseContext.getCurrentId();
        List<TagDO> tags = tagMapper.selectByUserId(userId);
        return tags.stream()
                .map(tag -> {
                    UserTagSimpleVO vo = new UserTagSimpleVO();
                    vo.setId(tag.getId());
                    vo.setTagName(tag.getTagName());
                    vo.setCreateTime(tag.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void assignUserTag(UserTagAssignDTO dto) {
        Long userId = BaseContext.getCurrentId();

        TagDO tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        assignTagToNote(tag, dto.getTargetId(), userId);
    }

    @Override
    public void removeUserTag(UserTagRemoveDTO dto) {
        Long userId = BaseContext.getCurrentId();

        TagDO tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        removeTagFromNote(tag, dto.getTargetId(), userId);
    }

    /**
     * 分配标签给目标笔记
     * <p>- 先使用 {@link NoteCoreService#getById(Long)} 来获取笔记（自带身份校验）</p>
     * <p>- 使用{@link NoteRelationService#listTagMappingsByNoteId(Long)} 获取列表来校验是否存在重复绑定</p>
     * <p>- 若不存在重复，使用{@link NoteRelationService#batchInsertTagMappings(List)} 创建映射关系</p>
     * @throws BaseException 不存在笔记 | 已有关联映射 | 创建映射关系失败
     */
    private void assignTagToNote(TagDO tag, Long noteId, Long userId) {
        NoteDO note = noteCoreService.getById(noteId);
        // 显示校验所属权（实际上已经校验过了）
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能绑定到自己的笔记");
        }

        // 检查是否存在重复绑定
        List<NoteTagMappingDO> existingMappings = noteRelationService.listTagMappingsByNoteId(noteId);
        for (NoteTagMappingDO mapping : existingMappings) {
            if (mapping.getTagId() != null
                    && mapping.getTagId().equals(tag.getId())
                    && NoteConstant.NOT_DELETED.equals(mapping.getIsDeleted())) {
                throw new BaseException("该标签已绑定到此笔记");
            }
        }

        // 创建映射
        NoteTagMappingDO mapping = new NoteTagMappingDO();
        mapping.setNoteId(noteId);
        BeanUtils.copyProperties(tag, mapping);
        mapping.setStatus(tag.getAuditStatus());
        mapping.setIsDeleted(NoteConstant.NOT_DELETED);

        int count = noteRelationService.batchInsertTagMappings(List.of(mapping));
        if (count <= 0) {
            throw new BaseException("绑定标签失败");
        }
    }

    /**
     * 移除标签从目标笔记
     * <p>- 先使用 {@link NoteCoreService#getById(Long)} 来获取笔记（自带身份校验）</p>
     * <p>- 使用{@link NoteRelationService#listTagMappingsByNoteId(Long)} 获取列表来校验是否存在映射关系</p>
     * <p>- 若存在映射关系，使用{@link NoteRelationService#unbindTagMappingById(Long)} 删除映射关系</p>
     * @throws BaseException 不存在笔记 | 未找到映射关系 | 删除映射关系失败
     */
    private void removeTagFromNote(TagDO tag, Long noteId, Long userId) {
        NoteDO note = noteCoreService.getById(noteId);
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能操作自己的笔记");
        }

        // 获取映射关系
        List<NoteTagMappingDO> mappings = noteRelationService.listTagMappingsByNoteId(noteId);
        NoteTagMappingDO targetMapping = null;
        for (NoteTagMappingDO mapping : mappings) {
            if (mapping.getTagId() != null
                    && mapping.getTagId().equals(tag.getId())
                    && NoteConstant.NOT_DELETED.equals(mapping.getIsDeleted())) {
                targetMapping = mapping;
                break;
            }
        }

        // 检查是否找到对应的映射关系
        if (targetMapping == null) {
            throw new BaseException("该标签未绑定到此笔记");
        }

        // 删除映射关系
        int count = noteRelationService.unbindTagMappingById(targetMapping.getId());
        if (count <= 0) {
            throw new BaseException("解除绑定失败");
        }
    }

    @Override
    public List<TagNoteCountDTO> listDeleteChecksByIds(Long userId, List<Long> ids) {
        return tagMapper.selectDeleteChecksByIds(userId, ids);
    }

    @Override
    public int updateAuditStatusByIds(List<Long> ids, Short auditStatus) {
        return tagMapper.updateAuditStatusByIds(ids, auditStatus);
    }

    private String normalizeTagName(String tagName) {
        if (tagName == null) {
            return null;
        }
        return tagName.trim();
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    private void validateTagName(String tagName) {
        if (!StringUtils.hasText(tagName)) {
            throw new BaseException(TagConstant.TAG_NAME_REQUIRED);
        }
        if (tagName.length() > TagConstant.MAX_TAG_NAME_LENGTH) {
            throw new BaseException(TagConstant.TAG_NAME_TOO_LONG);
        }
    }

    private void validateTagId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }
    }
}

