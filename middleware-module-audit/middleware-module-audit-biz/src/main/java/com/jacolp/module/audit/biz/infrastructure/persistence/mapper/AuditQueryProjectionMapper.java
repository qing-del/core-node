package com.jacolp.module.audit.biz.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuditQueryProjectionMapper {
    @Insert("INSERT INTO audit_query_record_projection " +
            "(target_type, audit_id, target_id, applicant_username, target_name, target_url, update_time) " +
            "VALUES (#{type}, #{auditId}, #{targetId}, #{applicantUsername}, #{targetName}, #{targetUrl}, NOW()) " +
            "ON DUPLICATE KEY UPDATE applicant_username=VALUES(applicant_username), " +
            "target_name=VALUES(target_name), target_url=VALUES(target_url), update_time=NOW()")
    int upsertRecord(@Param("type") String type, @Param("auditId") long auditId,
                     @Param("targetId") long targetId, @Param("applicantUsername") String applicantUsername,
                     @Param("targetName") String targetName, @Param("targetUrl") String targetUrl);

    @Insert("INSERT INTO audit_query_user_projection (user_id, username, nickname, update_time) " +
            "VALUES (#{id}, #{username}, #{nickname}, NOW()) " +
            "ON DUPLICATE KEY UPDATE username=VALUES(username), nickname=VALUES(nickname), update_time=NOW()")
    int upsertUser(@Param("id") long id, @Param("username") String username,
                   @Param("nickname") String nickname);

    @Select("SELECT username FROM audit_query_user_projection WHERE user_id = #{id}")
    String selectUsername(@Param("id") long id);

    @Update("UPDATE audit_query_record_projection SET reviewer_username = #{reviewerUsername}, " +
            "update_time = NOW() WHERE target_type = #{type} AND audit_id = #{auditId}")
    int captureReviewer(@Param("type") String type, @Param("auditId") long auditId,
                        @Param("reviewerUsername") String reviewerUsername);
}
