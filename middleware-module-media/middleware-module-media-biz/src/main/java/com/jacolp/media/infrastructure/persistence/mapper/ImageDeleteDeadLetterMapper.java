package com.jacolp.media.infrastructure.persistence.mapper;

import com.jacolp.media.infrastructure.persistence.dataobject.ImageDeleteDeadLetterDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ImageDeleteDeadLetterMapper {
    @Insert("INSERT INTO biz_image_delete_dead_letter " +
            "(resource_id, image_url, status, retry_count, create_time, update_time) " +
            "VALUES (#{resourceId}, #{imageUrl}, #{status}, 0, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImageDeleteDeadLetterDO row);

    @Select("SELECT id, resource_id AS resourceId, image_url AS imageUrl " +
            "FROM biz_image_delete_dead_letter WHERE status = #{status} ORDER BY id LIMIT 500 " +
            "FOR UPDATE SKIP LOCKED")
    List<ImageDeleteDeadLetterDO> selectBatch(short status);

    @Update("<script>UPDATE biz_image_delete_dead_letter SET status = #{status}, update_time = NOW() " +
            "WHERE status = 0 AND id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int markQueued(@Param("ids") List<Long> ids, @Param("status") short status);

    @Update("UPDATE biz_image_delete_dead_letter SET event_id = #{eventId}, status = #{status}, " +
            "update_time = NOW() WHERE id = #{id}")
    int attachEvent(@Param("id") long id, @Param("eventId") String eventId,
                    @Param("status") short status);

    @Update("UPDATE biz_image_delete_dead_letter SET status = #{status}, completed_time = NOW(), " +
            "last_error = NULL, update_time = NOW() WHERE id = #{id}")
    int markCompleted(@Param("id") long id, @Param("status") short status);

    @Update("UPDATE biz_image_delete_dead_letter SET status = #{status}, retry_count = retry_count + 1, " +
            "last_error = #{lastError}, update_time = NOW() WHERE id = #{id}")
    int markFailed(@Param("id") long id, @Param("status") short status,
                   @Param("lastError") String lastError);
}
