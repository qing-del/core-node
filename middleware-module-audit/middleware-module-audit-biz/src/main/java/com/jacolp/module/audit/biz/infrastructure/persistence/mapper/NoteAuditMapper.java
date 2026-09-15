package com.jacolp.module.audit.biz.infrastructure.persistence.mapper;

import com.jacolp.module.audit.biz.application.vo.NoteAuditVO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoteAuditMapper {
    List<NoteAuditVO> listByCondition(@Param("status") Short status, @Param("applicantUserId") Long applicantUserId);
    List<NoteAuditRecordDO> selectPendingByIds(@Param("ids") List<Long> ids);
    NoteAuditRecordDO selectPendingByNoteId(@Param("noteId") Long noteId);
    int batchReviewByIds(@Param("ids") List<Long> ids, @Param("status") Short status, @Param("reviewerUserId") Long reviewerUserId, @Param("rejectReason") String rejectReason);
    int insertAuditRecord(NoteAuditRecordDO record);
    @Select("SELECT COUNT(*) FROM biz_note_audit_record WHERE note_id = #{noteId} AND status = 0")
    int countPendingAuditByNoteId(@Param("noteId") Long noteId);
    @Delete("DELETE FROM biz_note_audit_record WHERE note_id = #{noteId} AND status = 0")
    int deletePendingByNoteId(@Param("noteId") Long noteId);
}
