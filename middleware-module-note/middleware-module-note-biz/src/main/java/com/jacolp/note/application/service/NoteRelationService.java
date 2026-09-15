package com.jacolp.note.application.service;

import java.util.List;
import java.util.Map;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.application.dto.image.ImageMappingBindDTO;
import com.jacolp.note.application.dto.note.EachMappingBindDTO;
import com.jacolp.note.application.dto.note.NoteMissingInfoDTO;
import com.jacolp.note.application.dto.tag.TagMappingBindDTO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteEachMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteImageMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteTagMappingDO;
import com.jacolp.note.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.note.application.vo.note.ImageBacklinkVO;
import com.jacolp.note.application.vo.note.NoteBacklinkVO;
import com.jacolp.note.application.vo.note.NoteCheckBindingVO;
import com.jacolp.note.application.vo.note.NoteRelationDetailVO;
import com.jacolp.note.application.vo.note.NoteSimpleVO;
import com.jacolp.note.application.vo.note.TagBacklinkVO;
import com.jacolp.media.api.model.MediaFileSummary;
import org.apache.ibatis.annotations.Param;

public interface NoteRelationService {

    /**
     * 批量插入 标签初始关系行关系
     * @param noteId 笔记 ID
     * @param tags 标签名称列表
     * @return 插入的行数
     */
    int initTagBatchInsertMappings(Long noteId, List<String> tags);


    /**
     * 批量插入 图片初始关系行关系
     * @param note 笔记
     * @param images 图片名称列表
     * @return 插入的行数
     */
    int initImageBatchInsertMappings(NoteDO note, List<String> images);


    /**
     * 批量插入初始关系行关系
     * @param noteId 笔记 ID
     * @param noteReflection 笔记标题列表
     * @return 插入的行数
     */
    int initNoteBatchInsertMappings(Long noteId, List<String> noteReflection);

    /**
     * 获取笔记关系详情
     * @param noteId 笔记 ID
     * @param tagMappings 标签关系列表
     * @param tagMap 标签列表
     * @param imageMappings 图片关系列表
     * @param imageMap 图片列表
     * @param eachMappings 关系列表
     * @param targetNoteMap 笔记列表
     * @return 笔记关系详情
     */
    NoteRelationDetailVO getRelationInfo(
            Long noteId,
            List<NoteTagMappingDO> tagMappings, Map<Long, TagDO> tagMap,
            List<NoteImageMappingDO> imageMappings, Map<Long, MediaFileSummary> imageMap,
            List<NoteEachMappingDO> eachMappings, Map<Long, NoteDO> targetNoteMap);

    /**
     * 绑定标签关系
     * @param dto
     */
    NoteTagMappingDO bindTagMapping(TagMappingBindDTO dto, TagDO targetTag);

    /**
     * 解绑标签关系
     * @param mappingId
     */
    NoteTagMappingDO unbindTagMapping(Long mappingId);

    /**
     * 绑定图片关系
     * @param dto
     * @return 被绑定的 映射行实体
     */
    NoteImageMappingDO bindImageMapping(ImageMappingBindDTO dto, MediaFileSummary targetImage);

    /**
     * 解绑图片关系
     * @param mappingId
     * @return 被取消绑定的 映射行实体
     */
    NoteImageMappingDO unbindImageMapping(Long mappingId);

    /**
     * 绑定关系
     * @param dto
     * @param targetNote
     * @return 被绑定的 映射行实体
     */
    NoteEachMappingDO bindEachMapping(EachMappingBindDTO dto, NoteDO targetNote);

    /**
     * 解绑关系
     * @param mappingId
     * @return 被取消绑定的 映射行实体
     */
    NoteEachMappingDO unbindEachMapping(Long mappingId);

    /**
     * 检查笔记关系是否完成
     * @param note
     * @return
     */
    NoteCheckBindingVO checkRelationCompletion(NoteDO note);

    /**
     * 获取笔记标签关系列表
     * @param noteId
     * @return
     */
    List<NoteTagMappingDO> listTagMappingsByNoteId(Long noteId);

    /**
     * 获取笔记图片关系列表
     * @param noteId 笔记 ID
     * @return 图片关系列表
     */
    List<NoteImageMappingDO> listImageMappingsByNoteId(Long noteId);

    /**
     * 获取笔记关系列表
     * @param noteId 笔记 ID
     * @return 笔记关系列表
     */
    List<NoteEachMappingDO> listEachMappingsByNoteId(Long noteId);

    /**
     * 批量插入标签关系
     * @param mappings
     * @return
     */
    int batchInsertTagMappings(List<NoteTagMappingDO> mappings);

    /**
     * 批量删除标签关系
     * <p>解除所有和这个标签相关的绑定</p>
     * @param tagId 标签 ID
     * @return 删除的行数
     */
    int unbindTagMappingById(Long tagId);

    /**
     * 尝试批量绑定标签。
     * <p>如果标签已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的标签映射行
     * @param tagMap 可供选择的标签
     * @throws BaseException 批量绑定失败
     */
    void tryBatchBindTagMappings(List<NoteTagMappingDO> mappings, Map<String, TagDO> tagMap);

    /**
     * 尝试批量绑定图片关系。
     * <p>如果图片已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的图片映射行
     * @param imageMap 可供选择的图片
     * @throws BaseException 批量绑定失败
     */
    void tryBatchBindImageMappings(List<NoteImageMappingDO> mappings, Map<String, MediaFileSummary> imageMap);

    /**
     * 尝试批量绑定关系。
     * <p>如果笔记已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的关系映射行
     * @param noteMap 可供选择的笔记
     * @throws BaseException 批量绑定失败
     */
    void tryBatchBindNoteMappings(List<NoteEachMappingDO> mappings, Map<String, NoteDO> noteMap);

    /**
     * 批量更新标签关系状态
     * @param tagIds 标签 ID
     * @param status 状态
     */
    void updateTagMappingPassByTagIds(List<Long> tagIds, Short status);

    /**
     * 批量更新图片关系状态
     * @param imageIds 图片 ID
     * @param status 状态
     */
    void updateImageMappingPassByImageIds(List<Long> imageIds, Short status);

    /**
     * 批量更新关系状态
     * @param sourceNoteIds 源笔记 ID
     * @param status 状态
     */
    void updateEachMappingPassBySourceNoteIds(List<Long> sourceNoteIds, Short status);


    /**
     * 批量删除 以 noteId 为源头的笔记关系
     * @param noteIds 笔记 ID 列表
     */
    void deleteByNoteIds(List<Long> noteIds);

    /**
     * 批量硬删除 以 noteId 为源头的笔记关系
     * @param noteIds 笔记 ID 列表
     */
    void hardDeleteByNoteIds(List<Long> noteIds);

    /**
     * 判断引用关系是否存在
     * @param id 被引用数据类型的 ID
     * @param type 被引用的类型：1-主题,2-标签,3-图片,4-笔记
     * @return 引用关系是否存在
     */
    // TODO 全局的默认策略就是被引用了不得被删除(*)
    boolean isRelated(Long id, Short type);

    /**
     * 判断引用关系是否存在
     * @param ids 被引用数据类型的 ID 列表
     * @param type 被引用的类型：1-主题,2-标签,3-图片,4-笔记
     * @return 引用关系是否存在
     */
    // TODO 全局的默认策略就是被引用了不得被删除(*)
    boolean isRelatedAll(List<Long> ids, Short type);

    /**
     * 计算笔记缺失信息
     * @param noteId 笔记 ID
     * @return 缺失信息数量
     */
    NoteMissingInfoDTO computeNoteMissingInfo(Long noteId);

    /**
     * 获取笔记关系的通过状态
     * @param noteId 笔记 ID
     * @return 如果笔记关系全部通过 返回 true 否则返回 false
     */
    boolean allMappingsPassed(Long noteId);

    /**
     * 获取图片简要列表
     * <p>- 此处没有权限校验</p>
     * @param imageId 图片 ID
     * @return 图片关系列表
     */
    List<NoteSimpleVO> listNoteSimplesByImageId(Long imageId);

    /**
     * 获取与该标签相关的笔记数量
     * @param tagId 标签 ID
     * @return 图片关系数量
     */
    long countRelationByTagId(@Param("tagId") Long tagId);

    /**
     * 判断笔记是否缺失标签
     * @param noteId 笔记 ID
     * @return 笔记是否缺失标签
     */
    boolean isMissingTags(Long noteId);

    /**
     * 判断笔记是否缺失图片
     * @param noteId 笔记 ID
     * @return 笔记是否缺失图片
     */
    boolean isMissingImages(Long noteId);

    /**
     * 判断笔记是否缺失笔记
     * @param noteId 笔记 ID
     * @return 笔记是否缺失笔记
     */
    boolean isMissingNotes(Long noteId);

    /**
     * 获取反向引用列表（哪些笔记引用了 noteId 这一笔记）
     * <p>- 此处没有目标笔记可见性校验，调用方需要保证 noteId 对当前 userId 的可见性</p>
     * <p>- 此处保证了被引用笔记的可见性</p>
     * @param noteId 被引用的笔记 ID
     * @param userId 当前用户 ID；为 null 时不做归属/公开过滤（管理端使用）
     * @return 反向引用列表
     */
    List<NoteBacklinkVO> listBacklinksByNoteId(Long noteId, Long userId);

    /**
     * 根据图片 id 统计图片映射行数量
     * @param imageId 图片 id
     * @return 图片映射行数量
     */
    int countByImageId(Long imageId);

    /**
     * 获取标签反向引用列表（哪些笔记引用了 tagId）
     * <p>- 此处没有目标标签可见性校验，调用方需要保证 tagId 对当前 userId 的可见性</p>
     * <p>- 此处保证了引用方笔记的可见性</p>
     * @param tagId 被引用的标签 ID
     * @param userId 当前用户 ID；为 null 时不做归属/公开过滤（管理端使用）
     * @return 标签反向引用列表
     */
    List<TagBacklinkVO> listBacklinksByTagId(Long tagId, Long userId);

    /**
     * 获取图片反向引用列表（哪些笔记引用了 imageId）
     * <p>- 此处没有目标图片可见性校验，调用方需要保证 imageId 对当前 userId 的可见性</p>
     * <p>- 此处保证了引用方笔记的可见性</p>
     * @param imageId 被引用的图片 ID
     * @param userId 当前用户 ID；为 null 时不做归属/公开过滤（管理端使用）
     * @return 图片反向引用列表
     */
    List<ImageBacklinkVO> listBacklinksByImageId(Long imageId, Long userId);
}
