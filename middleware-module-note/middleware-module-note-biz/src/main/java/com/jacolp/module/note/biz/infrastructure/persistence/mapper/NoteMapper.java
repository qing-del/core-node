package com.jacolp.module.note.biz.infrastructure.persistence.mapper;

import java.util.ArrayList;
import java.util.List;

import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.module.note.biz.application.vo.note.NoteSimpleVO;
import com.jacolp.module.note.biz.application.vo.note.NoteVO;

@Mapper
public interface NoteMapper {

    @Select("select ifnull(sum(md_file_size), 0) from biz_note where user_id = #{userId} and status != 8")
    Long sumNoteFileSizeByUserId(@Param("userId") Long userId);

    int insertNote(NoteDO note);
    int updateNote(NoteDO note);

    @Select("select id, user_id, topic_id, title, description, storage_type, status, is_changing, missing_info_mask, missing_count, md_file_size, create_time, update_time from biz_note where id = #{id}")
    NoteDO selectById(@Param("id") Long id);

    NoteVO selectVoById(@Param("id") Long id);
    List<NoteDO> selectByIds(@Param("ids") List<Long> ids);
    List<NoteVO> listByCondition(@Param("userId") Long userId, @Param("topicId") Long topicId,
                                 @Param("unclassified") Boolean unclassified, @Param("title") String title,
                                 @Param("status") Short status);
    List<NoteDO> selectByUserIdAndTitles(@Param("userId") Long userId, @Param("titles") List<String> titles);
    List<NoteDO> selectByUserIdAndTopicIdAndTitles(@Param("userId") Long userId, @Param("topicId") Long topicId,
                                                    @Param("titles") List<String> titles);
    ArrayList<NoteSimpleVO> selectNoteSimpleByImageId(@Param("imageId") Long imageId);
    int softDeleteByIds(@Param("ids") List<Long> ids);
    int updateStatus(@Param("id") Long id, @Param("status") Short status);
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") Short status);
    int updateMissingInfoFields(@Param("id") Long id, @Param("missingInfoMask") Integer mask,
                                @Param("missingCount") Integer count);
    int updateMissingCount(@Param("id") Long id, @Param("missingCount") Integer count);
    int updateNoteFieldsForCheck(@Param("id") Long id, @Param("status") Short status,
                                 @Param("missingInfoMask") Integer missingInfoMask,
                                 @Param("missingCount") Integer missingCount);
    int countByUserIdAndTopicIdAndTitle(@Param("userId") Long userId, @Param("topicId") Long topicId,
                                         @Param("originalFilename") String originalFilename);

    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND status != 8")
    long countByUserId(@Param("userId") Long userId);
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND status = 6")
    long countPublicByUserId(@Param("userId") Long userId);
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND status IN (5, 6)")
    long countApprovedByUserId(@Param("userId") Long userId);

    List<NoteVO> listByUserCondition(@Param("userId") Long userId, @Param("topicId") Long topicId,
                                     @Param("unclassified") Boolean unclassified, @Param("title") String title,
                                     @Param("globalScope") boolean globalScope);
    List<NoteVO> listPublicPublished(@Param("topicId") Long topicId, @Param("keyword") String keyword);
    NoteVO selectPublicPublishedVoById(@Param("id") Long id);
    @Select("SELECT status FROM biz_note WHERE id = #{noteId}")
    Short selectStatusById(Long noteId);
}
