package com.jacolp.note.application.facade;

import com.jacolp.note.infrastructure.persistence.dataobject.NoteEachMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteImageMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteTagMappingDO;
import com.jacolp.note.application.dto.image.ImageMappingBindDTO;
import com.jacolp.note.application.dto.note.EachMappingBindDTO;
import com.jacolp.note.application.dto.tag.TagMappingBindDTO;
import com.jacolp.note.application.vo.image.ImageSimpleVO;
import com.jacolp.note.application.vo.note.ImageBacklinkVO;
import com.jacolp.note.application.vo.note.NoteBacklinkVO;
import com.jacolp.note.application.vo.note.NoteCheckBindingVO;
import com.jacolp.note.application.vo.note.NoteRelationDetailVO;
import com.jacolp.note.application.vo.note.TagBacklinkVO;

import java.util.List;

public interface NoteRelationFacade {
    /**
     * 获取笔记关联信息
     * @param noteId
     * @return
     */
    NoteRelationDetailVO getRelationInfo(Long noteId);

    /**
     * 获取图片简要列表
     * <p>- 此处没有权限校验</p>
     */
    List<ImageSimpleVO> listImageSimpleVOsByNoteId(Long noteId);

    /**
     * 绑定标签映射关系
     * @param dto
     */
    void bindTagMapping(TagMappingBindDTO dto);

    /**
     * 解绑标签关系
     * @param mappingId
     */
    NoteTagMappingDO unbindTagMapping(Long mappingId);

    /**
     * 绑定图片映射关系
     * @param dto
     */
    void bindImageMapping(ImageMappingBindDTO dto);

    /**
     * 解绑图片关系
     * @param mappingId
     * @return 被取消绑定的 映射行实体
     */
    NoteImageMappingDO unbindImageMapping(Long mappingId);

    /**
     * 绑定笔记映射关系
     * @param dto
     */
    void bindEachMapping(EachMappingBindDTO dto);

    /**
     * 解绑关系
     * @param mappingId
     * @return 被取消绑定的 映射行实体
     */
    NoteEachMappingDO unbindEachMapping(Long mappingId);

    /**
     * 检查笔记关联信息是否完整
     * @param noteId 笔记 ID
     * @return 缺失的笔记关联信息
     */
    NoteCheckBindingVO checkRelationCompletion(Long noteId);

    /**
     * 查询反向引用列表（哪些笔记引用了 noteId）
     * <p>- 用户端：校验目标笔记可见性（拥有者 或 status=6 已公开）</p>
     * <p>- 管理端：跳过可见性校验</p>
     * @param noteId 被引用的笔记 ID
     * @return 反向引用列表
     */
    List<NoteBacklinkVO> listBacklinksByNoteId(Long noteId);

    /**
     * 查询标签反向引用列表（哪些笔记引用了 tagId）
     * <p>- 用户端：校验目标标签可见性（拥有者 或 auditStatus=2 已通过审核）</p>
     * <p>- 管理端：跳过可见性校验</p>
     * @param tagId 被引用的标签 ID
     * @return 标签反向引用列表
     */
    List<TagBacklinkVO> listBacklinksByTagId(Long tagId);

    /**
     * 查询图片反向引用列表（哪些笔记引用了 imageId）
     * <p>- 用户端：校验目标图片可见性（拥有者 或 isPublic=1 已公开）</p>
     * <p>- 管理端：跳过可见性校验</p>
     * @param imageId 被引用的图片 ID
     * @return 图片反向引用列表
     */
    List<ImageBacklinkVO> listBacklinksByImageId(Long imageId);
}
