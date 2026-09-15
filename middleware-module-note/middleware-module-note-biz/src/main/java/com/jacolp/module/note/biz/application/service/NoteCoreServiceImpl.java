package com.jacolp.module.note.biz.application.service;

import java.util.List;

import com.jacolp.module.note.biz.application.dto.note.*;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.enums.NoteStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.module.note.biz.application.vo.note.NoteStatsVO;
import com.jacolp.module.note.biz.application.vo.note.NoteVO;
import com.jacolp.result.PageResult;
import com.jacolp.module.audit.api.AuditApplicationApi;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.module.audit.api.CreateAuditApplicationCommand;

import lombok.extern.slf4j.Slf4j;

/**
 * 笔记核心 CRUD 实现。
 *
 * <p>负责 {@code biz_note} 表的基础读写及生命周期管理（发布/下架、审核申请、删除）。
 * 复杂编排（上传、修改、确认变更）由上层应用编排负责。</p>
 */
@Service
@Slf4j
public class NoteCoreServiceImpl implements NoteCoreService {

    @Autowired private NoteMapper noteMapper;

    @Autowired private AuditApplicationApi auditApi;

    @Override
    public void update(NoteDO noteEntity) {
        int affected = noteMapper.updateNote(noteEntity);
        if (affected <= 0) {
            throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
        }
    }

    @Override
    public PageResult listNotes(NoteQueryDTO dto) {
        if (dto == null) {
            dto = new NoteQueryDTO();
        }
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());

        List<NoteVO> records = noteMapper.listByCondition(dto.getUserId(), dto.getTopicId(),
                dto.getUnclassified(), dto.getTitle(), dto.getStatus());
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public NoteStatsVO getUserNoteOverview() {
        Long userId = BaseContext.getCurrentId();
        long noteTotalCount = noteMapper.countByUserId(userId);
        long publicNoteCount = noteMapper.countPublicByUserId(userId);
        long approvedNoteCount = noteMapper.countApprovedByUserId(userId);
        return new NoteStatsVO(noteTotalCount, publicNoteCount, approvedNoteCount);
    }

    @Override
    public PageResult listUserNotes(UserNoteQueryDTO dto) {
        if (dto == null) {
            dto = new UserNoteQueryDTO();
        }
        String title = (dto.getTitle() != null && !dto.getTitle().trim().isEmpty()) ? dto.getTitle().trim() : null;
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<NoteVO> records = noteMapper.listByUserCondition(
                BaseContext.getCurrentId(), dto.getTopicId(), dto.getUnclassified(), title, false);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 获取笔记
     * <p>会通过 {@link PermissionContext} 来判断是否是管理员</p>
     * <p>- 如果是管理员会跳过校验</p>
     * <p>- 如果不是管理员会校验笔记归属权</p>
     * @param id 笔记 ID
     * @return 笔记（自己的笔记 / 公开的笔记）
     * @throws BaseException 笔记不存在 / 笔记无权限访问
     */
    @Override
    public NoteDO getById(Long id) {
        NoteDO note = noteMapper.selectById(id);
        // 校验笔记是否存在
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        // 管理端越过所有权校验
        if (!PermissionContext.isAdmin()
                && !note.getUserId().equals(BaseContext.getCurrentId())) {
            throw new BaseException(UserConstant.PERMISSION_DENIED);
        }
        return note;
    }

    @Override
    public NoteDO getEntityById(Long id) {
        NoteDO note = noteMapper.selectById(id);
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        return note;
    }

    @Override
    public List<NoteDO> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return noteMapper.selectByIds(ids);
    }

    @Override
    public boolean countByUserIdAndTopicIdAndTitle(Long userId, Long topicId, String title) {
        return noteMapper.countByUserIdAndTopicIdAndTitle(userId, topicId, title) > 0;
    }

    @Override
    public List<NoteDO> getByUserIdAndTopicIdAndTitles(Long userId, Long topicId, List<String> titles) {
        if (titles == null) {
            return List.of();
        }

        List<NoteDO> notes = noteMapper.selectByUserIdAndTitles(userId, titles);
        if (notes == null || notes.isEmpty()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        return notes;
    }

    @Override
    public void saveNote(NoteDO noteEntity) {
        if (noteMapper.insertNote(noteEntity) <= 0) {
            throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
        }
    }

    @Override
    public void adminDeleteConverted(Long noteId) {
        noteMapper.updateStatus(noteId, NoteStatus.READY_TO_CONVERT.getCode());
    }

    @Override
    public int updateStatusByIds(List<Long> noteIds, Short status) {
        if (noteIds != null && !noteIds.isEmpty()) {
            int affected = noteMapper.updateStatusByIds(noteIds, status);
            if (affected != noteIds.size()) {
                log.error("Failed to update a part of notes' status!");
                if (affected < 1) {
                    throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
                }
            }
            return affected;
        }
        return 0;
    }

    @Override
    public void modifyInfo(NoteModifyInfoDTO dto) {
        NoteDO note = getById(dto.getId());

        if (StringUtils.hasText(dto.getDescription())) {
            note.setDescription(dto.getDescription().trim());
        }

        note.setTopicId(dto.getTopicId());
        // title 不允许通过 modifyInfo 修改

        if (noteMapper.updateNote(note) < 1) {
            log.error("Failed to update note info!");
            throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
        }
    }

    // ==================== 审核申请 ====================

    /**
     * 用户端发起笔记审核申请。
     * <p>仅允许申请自己的笔记，且不能已通过审核或已有待审核申请。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitNoteAudit(Long noteId) {
        Long userId = BaseContext.getCurrentId();
        if (noteId == null || noteId <= 0) {
            throw new BaseException(NoteConstant.NOTE_ID_INVALID);
        }

        NoteDO note = getById(noteId);

        // 检查笔记状态是否可以发生转换
        NoteStatus status = NoteStatus.fromCode(note.getStatus());
        if (status.isApproved() || status.isPublished()) {
            throw new BaseException(NoteConstant.NOTE_ALREADY_PASSED);
        }
        if (!status.canTransitionTo(NoteStatus.PENDING_AUDIT)) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        if (noteMapper.updateStatusIfCurrent(noteId, status.getCode(), NoteStatus.PENDING_AUDIT.getCode()) != 1) {
            throw new BaseException(NoteConstant.NOTE_AUDIT_PENDING);
        }
        auditApi.createApplication(new CreateAuditApplicationCommand(AuditTargetType.NOTE, noteId, userId,
                null, note.getTitle(), null));

        // 更新笔记状态
    }

    /**
     * 用户端撤销笔记审核申请。
     * <p>仅允许撤销自己处于 PENDING_AUDIT 状态的笔记，撤销后状态回退到 CONVERTED。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelNoteAudit(Long noteId) {
        if (noteId == null || noteId <= 0) {
            throw new BaseException(NoteConstant.NOTE_ID_INVALID);
        }

        NoteDO note = getById(noteId);

        // 仅 PENDING_AUDIT 状态可撤销，且需符合状态机的转换规则
        NoteStatus status = NoteStatus.fromCode(note.getStatus());
        if (status != NoteStatus.PENDING_AUDIT
                || !status.canTransitionTo(NoteStatus.CONVERTED)) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }

        auditApi.cancelApplication(new CancelAuditApplicationCommand(AuditTargetType.NOTE, noteId,
                BaseContext.getCurrentId()));

        // 状态回退到 CONVERTED
        if (noteMapper.updateStatusIfCurrent(noteId, NoteStatus.PENDING_AUDIT.getCode(),
                NoteStatus.CONVERTED.getCode()) != 1) {
            throw new BaseException(NoteConstant.NOTE_STATUS_NOT_ALLOWED);
        }
    }

    // ==================== 用户端查询 ====================

    /**
     * 用户端关键词搜索（仅查自己的笔记）。
     */
    @Override
    public PageResult listUserNotesBySearch(UserNoteSearchDTO dto) {
        Long userId = BaseContext.getCurrentId();
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<NoteVO> records = noteMapper.listByUserCondition(
                userId, dto.getTopicId(), dto.getUnclassified(), dto.getKeyword(), false);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 用户端全文搜索。
     */
    @Override
    public PageResult searchUserNotes(UserNoteSearchDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (!StringUtils.hasText(dto.getKeyword())) {
            throw new BaseException("搜索关键词不能为空");
        }

        String keyword = dto.getKeyword().trim();
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<NoteVO> records = noteMapper.listByUserCondition(
                userId, dto.getTopicId(), dto.getUnclassified(), keyword, false);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 插入笔记。
     * <p>插入笔记行、转换结果行、文本行、映射行。</p>
     * @return 插入到数据库之后生成的笔记 ID
     */
    @NonNull
    public Long insertNote(UploadToInsertNoteDTO dto) {
        // 构建笔记的实体类
        NoteDO note = new NoteDO();
        note.setUserId(dto.getUserId());
        note.setTopicId(dto.getTopicId());
        note.setTitle(stripMarkdownExtension(dto.getOriginalFilename()));
        note.setStorageType(NoteConstant.DEFAULT_STORAGE_TYPE);
        note.setMdFileSize(dto.getFileSize());

        // 计算缺失信息掩码
        int missingMask = calculateInitMissingInfoMask(dto.getTags(), dto.getImageNames(), dto.getNoteNames());
        int missingCount = countInitMissingBits(missingMask);

        // 设置初始状态
        note.setStatus(NoteStatus.NEW.getCode());   // 设置笔记状态位 NEW
        note.setMissingInfoMask(missingMask);
        note.setMissingCount(missingCount);

        // 插入笔记行
        int affected = noteMapper.insertNote(note);
        if (affected <= 0) {
            throw new BaseException(NoteConstant.NOTE_UPLOAD_FAILED);
        }
        return note.getId();
    }

    /**
     * 获取笔记的 VO。
     * <p>- 会根据 {@link PermissionContext} 与 {@link NoteVO#getStatus()} 是否公开 来判断 是否要校验所有权</p>
     * @param noteId 笔记 ID
     * @return 笔记的 VO
     * @throws BaseException 笔记不存在 / 笔记无权限访问 / 笔记未公开且无归属权
     */
    @Override
    public NoteVO getNoteVOById(Long noteId) {
        NoteVO noteVO = noteMapper.selectVoById(noteId);
        if (noteVO == null || NoteStatus.DELETED.getCode().equals(noteVO.getStatus())) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 检查是否是管理员 & 校验所有权
        if (!noteVO.getStatus().equals(NoteStatus.PUBLISHED.getCode())
                && !PermissionContext.isAdmin()
                && !noteVO.getUserId().equals(BaseContext.getCurrentId())) {
            throw new BaseException(NoteConstant.NOTE_NOT_OWNER);
        }

        return noteVO;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    /** 按解析出的关联信息计算初始缺失掩码 — 有关联就标记为缺失 */
    private int calculateInitMissingInfoMask(List<String> tags, List<String> imageNames,
                                             List<String> noteLinks) {
        int missingMask = 0;
        if (tags != null && !tags.isEmpty()) missingMask |= NoteConstant.MISSING_TAG;
        if (imageNames != null && !imageNames.isEmpty()) missingMask |= NoteConstant.MISSING_IMAGE;
        if (noteLinks != null && !noteLinks.isEmpty()) missingMask |= NoteConstant.MISSING_NOTE;
        return missingMask;
    }

    /** 去除 .md 后缀 */
    private String stripMarkdownExtension(String filename) {
        if (filename == null) return null;
        String name = filename.trim();
        if (name.toLowerCase().endsWith(".md")) {
            return name.substring(0, name.length() - 3).trim();
        }
        return name;
    }

    /** 统计缺失位数量（位运算 popcount） */
    private int countInitMissingBits(int missingMask) {
        int count = 0;
        if ((missingMask & NoteConstant.MISSING_TAG) != 0) count++;
        if ((missingMask & NoteConstant.MISSING_IMAGE) != 0) count++;
        if ((missingMask & NoteConstant.MISSING_NOTE) != 0) count++;
        return count;
    }
}
