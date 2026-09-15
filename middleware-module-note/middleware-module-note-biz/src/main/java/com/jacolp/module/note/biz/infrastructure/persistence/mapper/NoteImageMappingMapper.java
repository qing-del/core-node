package com.jacolp.module.note.biz.infrastructure.persistence.mapper;

import java.util.ArrayList;
import java.util.List;



import com.jacolp.module.note.biz.application.vo.note.NoteSimpleVO;
import com.jacolp.module.note.biz.infrastructure.persistence.projection.MappingProjections;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteImageMappingDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoteImageMappingMapper {

    List<NoteImageMappingDO> selectByNoteId(@Param("noteId") Long noteId);

    NoteImageMappingDO selectById(@Param("id") Long id);

    /**
     * 根据 id 查询笔记图片映射行，并验证用户 id
     * @param mappingId 笔记图片映射 id
     * @param userId 用户 id（传入null的话不开启校验）
     * @return 笔记图片映射行 （不存在 / 没有所属权的时候返回 null）
     */
    NoteImageMappingDO selectByIdWithValidUserId(@Param("id") Long mappingId, Long userId);

    int bindImageById(@Param("id") Long id,
                      @Param("imageId") Long imageId,
                      @Param("imageUserId") Long imageUserId,
                      @Param("isCrossUser") Short isCrossUser,
                      @Param("status") Short status);

    int batchBindImageByIds(@Param("mappings") List<NoteImageMappingDO> mappings);

    int unbindImageById(@Param("id") Long id);

    /**
     * 批量插入/更新数据库表的数据
     * <p>需要保证存在 `note_id` 和 `image_id`</p>
     * <p>否则会造成数据拷贝的后果</p>
     * @param mappings
     * @return
     */
    int batchInsertMappings(@Param("mappings") List<NoteImageMappingDO> mappings);

    int softDeleteByNoteId(@Param("noteId") Long noteId);

    int softDeleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    int hardDeleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    int softDeleteByNoteIdAndParsedImageNames(@Param("noteId") Long noteId,
                                              @Param("parsedImageNames") List<String> parsedImageNames);

    int deleteSoftDeletedRows();

    /**
     * 统计指定笔记的图片映射数量
     * @param noteId 笔记 id
     * @param status 状态快照（传入null代表全查）
     * @return 笔记图片映射行数量
     */
    int countByNoteIdAndStatus(@Param("noteId") Long noteId,
                               @Param("status") Short status);

    int updateByImageIds(@Param("imageIds") List<Long> imageIds,
                         @Param("status") Short status);
    @Update("UPDATE biz_note_image_mapping SET status = #{status}, update_time = NOW() " +
            "WHERE image_id = #{imageId} AND is_deleted = 0")
    int updateActiveByImageId(@Param("imageId") Long imageId, @Param("status") Short status);

    /**
     * 统计指定笔记的未绑定图片数量
     * @param noteId 笔记 id
     * @return 未绑定图片映射行数量
     */
    int countByNoteIdAndImageIdIsNull(@Param("noteId") Long noteId);

    /**
     * 根据图片 id 查询笔记
     * @param imageId
     * @return
     */
    ArrayList<NoteSimpleVO> selectNoteSimpleByImageId(@Param("imageId") Long imageId);

    /**
     * 根据图片 id 统计图片映射行数量
     * @param imageId 图片 id
     * @return 图片映射行数量
     */
    @Select("SELECT COUNT(1) FROM biz_note_image_mapping WHERE image_id = #{imageId} AND is_deleted = 0")
    int countByImageId(Long imageId);

    List<MappingProjections.ImageNoteCount> countByImageIds(@Param("imageIds") List<Long> imageIds);

    List<NoteImageMappingDO> selectActiveByImageIds(@Param("imageIds") List<Long> imageIds);

    /**
     * 查询引用了指定图片的源笔记列表（图片反向引用）
     * @param imageId 目标图片 id
     * @param userId 当前用户 id；传 null 时跳过归属/公开过滤（管理端使用）
     * @return 反向引用列表（包含源笔记标题、状态、跨用户标记等）
     */
    List<MappingProjections.ImageBacklink> selectBacklinksByImageId(@Param("imageId") Long imageId,
                                                   @Param("userId") Long userId);

    /**
     * 查询访客可见的公开笔记图片摘要。
     */
    List<MappingProjections.ImageSimple> selectPublicImagesByNoteId(@Param("noteId") Long noteId);
}
