package com.jacolp.module.media.biz.infrastructure.persistence.mapper;

import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDeleteDeadLetterDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImageDeleteDeadLetterMapper {
    int insertBatch(List<ImageDeleteDeadLetterDO> list);
    int updateBatch(List<Long> ids, short status);

    @Select("SELECT id, image_url AS imageUrl FROM biz_image_delete_dead_letter WHERE status = #{status} limit 500")
    List<ImageDeleteDeadLetterDO> selectBatch(short status);
}
