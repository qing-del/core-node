package com.jacolp.note.application.service;

import java.util.List;

import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.note.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.note.infrastructure.persistence.dto.TagNoteCountDTO;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.note.application.dto.tag.TagAddDTO;
import com.jacolp.note.application.dto.tag.TagBatchAddDTO;
import com.jacolp.note.application.dto.tag.TagModifyDTO;
import com.jacolp.note.application.dto.tag.TagQueryDTO;
import com.jacolp.note.application.dto.tag.UserTagAssignDTO;
import com.jacolp.note.application.dto.tag.UserTagQueryDTO;
import com.jacolp.note.application.dto.tag.UserTagRemoveDTO;
import com.jacolp.note.application.vo.tag.TagBatchAddVO;
import com.jacolp.note.application.vo.tag.TagStatsVO;
import com.jacolp.note.application.vo.tag.UserTagSimpleVO;

public interface TagService {

    void addTag(TagAddDTO dto);

    TagBatchAddVO batchAddTags(TagBatchAddDTO dto);

    void modifyTag(TagModifyDTO dto);

    /**
     * 删除标签
     * <p>- 查询待删除的标签列表时，会根据 {@link PermissionContext#isAdmin()} 来判断是否需要开启用户过滤</p>
     * @param ids
     */
    void deleteTags(List<Long> ids);

    PageResult listTags(TagQueryDTO dto);

    /**
     * 用户端条件查询：当前用户自己的标签 + 别人已通过审核的标签。
     */
    PageResult listUserTags(UserTagQueryDTO dto);

    /**
     * 用户端发起标签审核申请。
     */
    void submitTagAudit(Long tagId);

    /**
     * 用户端撤销标签审核申请。
     */
    void cancelTagAudit(Long tagId);

    /**
     * 获取当前用户标签统计。
     */
    TagStatsVO getUserTagStats();

    /**
     * 根据ID和用户ID查询标签，供其他Service内部调用。
     */
    TagDO getByIdAndUserId(Long id, Long userId);

    /**
     * 根据ID列表批量查询标签，供其他Service内部调用。
     */
    List<TagDO> getByIds(List<Long> ids);

    /**
     * 根据标签名列表和用户ID批量查询标签，供其他Service内部调用。
     */
    List<TagDO> getByNamesAndUserId(List<String> names, Long userId);

    // ===== 用户端方法 =====

    List<UserTagSimpleVO> listUserTagSimples();

    void assignUserTag(UserTagAssignDTO dto);

    void removeUserTag(UserTagRemoveDTO dto);

    List<TagNoteCountDTO> listDeleteChecksByIds(Long userId, List<Long> ids);

    int updateAuditStatusByIds(List<Long> ids, Short auditStatus);
}
