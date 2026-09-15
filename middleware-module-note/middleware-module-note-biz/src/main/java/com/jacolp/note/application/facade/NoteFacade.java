package com.jacolp.note.application.facade;

import java.util.List;

import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.context.NoteImageResolveContext;
import com.jacolp.note.infrastructure.cache.GuestCacheConstant;
import com.jacolp.note.infrastructure.cache.GuestCacheEvict;
import com.jacolp.note.application.vo.note.NoteDetailVO;
import com.jacolp.note.application.vo.note.NoteDiffVO;
import com.jacolp.note.application.vo.note.NoteModifyDiffDetailVO;
import com.jacolp.note.application.vo.note.NoteUploadVO;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.note.application.dto.note.NoteChangeConfirmDTO;

public interface NoteFacade {
    /**
     * 上传笔记 —— 完整 5 步编排。
     *
     * <ol>
     *   <li>校验主题存在性 + 同主题同名唯一性</li>
     *   <li>扫描 Markdown 提取标签、图片、内联笔记</li>
     *   <li>计算缺失信息掩码后插入笔记行</li>
     *   <li>将 Markdown 原文写入内容表</li>
     *   <li>批量建立标签/图片/内联笔记三类映射（初始全部未绑定）</li>
     * </ol>
     *
     * @param file 笔记源文件
     * @param topicId 笔记主题
     * @return 上传结果 -- 笔记缺失的关联信息
     */
    NoteUploadVO uploadNote(MultipartFile file, Long topicId);

    /**
     * 修改笔记源文件 —— 完整 5 步编排。
     *
     * <ol>
     *   <li>校验笔记所有权 + 检查是否存在未确认的 diff</li>
     *   <li>从内容表读取旧 Markdown，新内容暂存到 {@code markdown_content_new} 列</li>
     *   <li>分别扫描新旧文本的标签/图片/内联笔记</li>
     *   <li>对比新旧扫描结果构建 DiffVO（新增/移除列表）</li>
     *   <li>持久化 diff 记录（状态=待确认），供后续确认或取消</li>
     * </ol>
     *
     * @param noteId 笔记 ID
     * @param file 更新笔记的源文件
     * @return DiffVO -- 新旧标签/图片/内联笔记列表
     * @throws BaseException 笔记不存在、权限不足、笔记已存在未确认的变更
     */
    NoteDiffVO modifyNoteSource(Long noteId, MultipartFile file);

    /**
     * 确认或取消笔记变更。
     *
     * <p><b>确认时</b>：用新内容覆盖旧内容 → 删除旧三类映射 → 重新扫描建立新映射 → 状态回退为 NEW。</p>
     * <p><b>取消时</b>：清除临时版本内容，旧内容不变。</p>
     *
     * @param noteId 笔记 ID
     * @param dto    confirm=true 确认，confirm=false 取消
     * @throws BaseException 笔记不存在、权限不足、笔记不存在未确认的变更
     */
    void confirmChange(Long noteId, NoteChangeConfirmDTO dto);

    /**
     * 获取修改笔记详情
     * @param noteId
     * @return
     */
    NoteModifyDiffDetailVO getModifyDiff(Long noteId);

    /**
     * 将笔记 Markdown 原文转换为 HTML
     * <p>前置校验：笔记不能处于"信息缺失"状态，且缺失计数必须为 0。
     * 转换前将当前 noteId 注入 {@link NoteImageResolveContext} 供图片插件使用。</p>
     * <p>- 管理员调用的话会跳过所有权的校验</p>
     */
    void convertNote(Long noteId);

    /**
     * 批量删除笔记 -- (管理员专属)
     * @param ids
     */
    void adminDeleteNotes(List<Long> ids);

    /**
     * 删除笔记
     *
     * <ol>
     *   <li>校验笔记存在且不处于审核中/已公开状态</li>
     *   <li>记录每个资源的存储释放事实</li>
     *   <li>依次清理转换结果 → Diff 记录 → 文本内容 → 三类映射</li>
     *   <li>笔记行状态标记为 DELETED（软删除）</li>
     *   <li>与软删除同事务写入存储释放事件</li>
     * </ol>
     */
    void deleteNote(Long noteId);


    /**
     * 更新笔记状态
     * <p>- 管理员调用不做所属校验</p>
     * <p>- 支持以下复杂逻辑的转换做了特殊处理：</p>
     * <ol>
     *     <li>- 已通过 -> 发布</li>
     * </ol>
     */
    @GuestCacheEvict(cacheNames = {
            GuestCacheConstant.GUEST_NOTE_LIST_CACHE,
            GuestCacheConstant.GUEST_NOTE_DETAIL_CACHE
    })
    void updateNoteStatus(Long noteId, Short status);

    /**
     * 获取笔记完整详情 --
     * <p>- 通过 {@link PermissionContext} 来控制是否校验所有权</p>
     * <p>聚合笔记基本信息、主题名、标签名列表、图片简要列表、
     * 双链映射及转换结果，供前端详情页一次性加载。</p>
     */
    NoteDetailVO getInfo(Long noteId);

    /**
     * 删除转换结果
     * @param noteId
     */
    void deleteConverted(Long noteId);
}
