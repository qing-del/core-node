package com.jacolp.module.audit.biz.infrastructure.persistence.mapper;

import com.jacolp.module.audit.biz.application.vo.MetaAuditVO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MetaAuditMapper {
    List<MetaAuditVO> listByCondition(@Param("applyType") Short applyType, @Param("status") Short status, @Param("applicantUserId") Long applicantUserId);
    List<MetaAuditRecordDO> selectPendingByIds(@Param("ids") List<Long> ids);
    MetaAuditRecordDO selectPendingByApplyTypeAndTargetId(@Param("applyType") Short applyType, @Param("targetId") Long targetId);
    int batchReviewByIds(@Param("ids") List<Long> ids, @Param("status") Short status, @Param("reviewerUserId") Long reviewerUserId, @Param("rejectReason") String rejectReason);
    int insertAuditRecord(MetaAuditRecordDO record);
    @Select("SELECT COUNT(*) FROM biz_tag_audit_record WHERE target_id = #{targetId} AND status = 1")
    int countPendingAuditByApplyTypeAndTargetId(@Param("applyType") Short applyType, @Param("targetId") Long targetId);
    @Update("UPDATE biz_tag_audit_record SET status = 0, update_time = NOW() WHERE target_id = #{targetId} AND status = 1")
    int deletePendingByApplyTypeAndTargetId(@Param("applyType") Short applyType, @Param("targetId") Long targetId);
}
