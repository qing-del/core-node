package com.jacolp.note.application.service;

import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.note.application.dto.note.*;
import com.jacolp.note.application.vo.note.NoteStatsVO;
import com.jacolp.note.application.vo.note.NoteVO;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface NoteCoreService {

    /**
     * 更新笔记
     * @param noteEntity 笔记
     * @throws BaseException 笔记不存在 / 更新失败
     */
    void update(NoteDO noteEntity);

    /**
     * 获取笔记列表
     *
     * @param dto 查询条件
     */
    PageResult listNotes(NoteQueryDTO dto);

    /**
     * 获取当前用户笔记统计（概览）
     */
    NoteStatsVO getUserNoteOverview();

    /**
     * 用户端条件查询：仅查询当前用户自己的笔记。
     */
    PageResult listUserNotes(UserNoteQueryDTO dto);

    /**
     * 获取笔记
     * <p>会通过 PermissionContext 来判断是否是管理员</p>
     * <p>- 如果是管理员会跳过校验</p>
     * <p>- 如果不是管理员会校验笔记归属权</p>
     * @param id 笔记 ID
     * @return 笔记
     * @throws BaseException 笔记不存在 / 笔记无权限访问
     */
    NoteDO getById(Long id);

    /**
     * 批量获取笔记
     * <p>- 此方法的逻辑层没有权限校验笔记所属权</p>
     * @param ids 笔记 ID
     * @return 笔记列表
     */
    List<NoteDO> getByIds(List<Long> ids);

    /**
     * 根据 ID 获取笔记实体（无所有权校验）
     * <p>- 仅供需要做自定义可见性校验的 Facade 内部使用</p>
     * <p>- 仍会校验存在性与是否软删除</p>
     * @param id 笔记 ID
     * @return 笔记实体
     * @throws BaseException 笔记不存在或已删除
     */
    NoteDO getEntityById(Long id);

    /**
     * 检查是否存在笔记
     *
     * @param userId  用户 ID
     * @param topicId 主题 ID
     * @param title   笔记标题
     * @return 存在返回 true
     */
    boolean countByUserIdAndTopicIdAndTitle(Long userId, Long topicId, String title);


    /**
     * 获取笔记
     *
     * @param userId  用户 ID
     * @param topicId 主题 ID
     * @param titles   笔记标题（如果传入 null 会导致返回 null）
     * @return 存在就返回笔记列表
     * @throws BaseException 不存在
     */
    List<NoteDO> getByUserIdAndTopicIdAndTitles(Long userId, Long topicId, List<String> titles);

    /**
     * 保存笔记
     *
     * @param noteEntity 笔记
     */
    void saveNote(NoteDO noteEntity);

    /**
     * 删除笔记转换结果 -- (管理员)
     * <p>不做笔记归属权的校验，直接删除笔记转换结果</p>
     *
     * @param noteId 笔记 ID
     */
    void adminDeleteConverted(Long noteId);

    /**
     * 批量更新笔记状态
     * <p>- 不支持回滚，仅作日志记录</p>
     *
     * @param noteIds 笔记 ID
     * @param status  笔记状态
     * @return
     * @throws BaseException 全部更新失败
     */
    int updateStatusByIds(List<Long> noteIds, Short status);

    /**
     * 修改笔记信息 -- （通用）
     *
     * @param dto 修改信息
     */
    void modifyInfo(NoteModifyInfoDTO dto);

    /**
     * 用户端发起笔记审核申请。
     *
     * @param noteId 笔记 ID
     */
    void submitNoteAudit(Long noteId);

    /**
     * 用户端撤销笔记审核申请。
     * <p>笔记状态从 PENDING_AUDIT 回退到 CONVERTED，同时删除待审核记录。</p>
     *
     * @param noteId 笔记 ID
     */
    void cancelNoteAudit(Long noteId);

    /**
     * 用户端关键词搜索（仅查自己的笔记）。
     *
     * @param dto 搜索条件，含关键词、主题、分页
     * @return 分页结果
     */
    PageResult listUserNotesBySearch(UserNoteSearchDTO dto);

    /**
     * 用户端全文搜索笔记。
     *
     * @param dto 搜索条件
     * @return 分页结果
     */
    PageResult searchUserNotes(UserNoteSearchDTO dto);

    /**
     * 插入笔记。
     * @return 笔记
     */
    @NonNull
    Long insertNote(UploadToInsertNoteDTO dto);

    /**
     * 获取笔记的 VO。
     * <p>- 会根据 {@link PermissionContext} 是否要校验所有权</p>
     * @param noteId 笔记 ID
     * @return 笔记的 VO
     * @throws BaseException 笔记不存在 / 笔记无权限访问
     */
    NoteVO getNoteVOById(Long noteId);
}
