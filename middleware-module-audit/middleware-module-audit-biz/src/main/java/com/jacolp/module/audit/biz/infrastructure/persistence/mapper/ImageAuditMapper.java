package com.jacolp.module.audit.biz.infrastructure.persistence.mapper;

import com.jacolp.module.audit.biz.application.vo.ImageAuditVO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ImageAuditMapper {
    @Select("SELECT id, applicant_user_id, image_id, apply_reason, status, reviewer_user_id, reject_reason, create_time, review_time, update_time FROM biz_image_audit_record WHERE id = #{id}")
    ImageAuditRecordDO selectById(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM biz_image_audit_record WHERE image_id = #{imageId} AND status = 1")
    int countPendingAuditByImageId(@Param("imageId") Long imageId);
    int insertAuditRecord(ImageAuditRecordDO record);
    int updateAuditRecord(ImageAuditRecordDO record);
    List<ImageAuditVO> listByCondition(@Param("status") Short status, @Param("applicantUserId") Long applicantUserId);
    List<ImageAuditVO> listPendingAudits();
    List<ImageAuditRecordDO> selectPendingByIds(@Param("ids") List<Long> ids);
    ImageAuditRecordDO selectPendingByImageId(@Param("imageId") Long imageId);
    int batchReviewByIds(@Param("ids") List<Long> ids, @Param("status") Short status, @Param("reviewerUserId") Long reviewerUserId, @Param("rejectReason") String rejectReason);
    @Update("UPDATE biz_image_audit_record SET status = 0, update_time = NOW() WHERE image_id = #{imageId} AND status = 1")
    int deletePendingByImageId(@Param("imageId") Long imageId);
}
