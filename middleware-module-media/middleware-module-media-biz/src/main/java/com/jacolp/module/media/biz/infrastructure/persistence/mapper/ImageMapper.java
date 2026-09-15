package com.jacolp.module.media.biz.infrastructure.persistence.mapper;

import com.jacolp.module.media.biz.application.vo.image.ImageVO;
import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ImageMapper {
    @Select("SELECT id, user_id AS userId, topic_id AS topicId, filename, oss_url AS ossUrl, storage_type AS storageType, file_size AS fileSize, is_public AS isPublic, audit_status AS auditStatus, upload_time AS uploadTime FROM biz_image WHERE id = #{id} AND audit_status != 4")
    ImageDO selectById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM biz_image WHERE user_id = #{userId} AND topic_id = IFNULL(#{topicId}, 0) AND filename = #{filename} AND audit_status != 4")
    int countByUserIdTopicIdAndFilename(@Param("userId") Long userId, @Param("topicId") Long topicId, @Param("filename") String filename);

    int insertImage(ImageDO image);
    int updateImage(ImageDO image);
    List<ImageVO> listByCondition(ImageDO query);
    int deleteByIds(@Param("ids") List<Long> ids);
    int updateAuditStatusByIds(@Param("ids") List<Long> ids, @Param("auditStatus") Short auditStatus);
    @Update("UPDATE biz_image SET audit_status = #{newStatus} " +
            "WHERE id = #{id} AND audit_status = #{expectedStatus}")
    int updateAuditStatusIfCurrent(@Param("id") Long id, @Param("expectedStatus") Short expectedStatus,
                                   @Param("newStatus") Short newStatus);

    @Select("SELECT IFNULL(SUM(file_size), 0) FROM biz_image WHERE user_id = #{userId} AND audit_status != 4")
    Long sumImageFileSizeByUserId(@Param("userId") Long userId);

    List<ImageDO> selectByIds(@Param("ids") List<Long> ids);

    @Select("SELECT id, user_id AS userId, topic_id AS topicId, filename, oss_url AS ossUrl, storage_type AS storageType, file_size AS fileSize, is_public AS isPublic, audit_status AS auditStatus, upload_time AS uploadTime FROM biz_image WHERE user_id = #{userId} AND filename = #{filename} AND audit_status != 4 LIMIT 1")
    ImageDO selectByUserIdAndFilename(@Param("userId") Long userId, @Param("filename") String filename);

    List<ImageDO> selectByUserIdAndTopicIdAndFilenames(@Param("userId") Long userId,
                                                        @Param("topicId") Long topicId,
                                                        @Param("filenames") List<String> filenames);
    List<ImageVO> listByUserCondition(@Param("userId") Long userId, @Param("topicId") Long topicId,
                                      @Param("filename") String filename, @Param("globalScope") boolean globalScope);

    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_image WHERE user_id = #{userId} AND audit_status != 4")
    long countByUserId(@Param("userId") Long userId);
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_image WHERE user_id = #{userId} AND audit_status = 2")
    long countPassedByUserId(@Param("userId") Long userId);
}
