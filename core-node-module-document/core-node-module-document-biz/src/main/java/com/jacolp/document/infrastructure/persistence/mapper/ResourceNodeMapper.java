package com.jacolp.document.infrastructure.persistence.mapper;

import com.jacolp.document.infrastructure.persistence.dataobject.ResourceNodeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 持久化跨模块资源节点；节点本身不依赖目标资源表的外键或唯一约束。 */
@Mapper
public interface ResourceNodeMapper {

    /** 创建一个资源节点并回填自增 ID。 */
    int insert(ResourceNodeDO resourceNode);

    /** 更新已有节点的资源类型和目标 ID，供同一 refId 重新绑定时复用节点。 */
    int updateTargetById(@Param("id") Long id, @Param("resourceType") String resourceType,
                         @Param("targetId") Long targetId);
}
