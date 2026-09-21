package com.jacolp.document.infrastructure.persistence.mapper;

import java.util.List;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentOpLogMapper {

    /**
     * 写入一段 Redis Stream 截断批次。已有的 Redis/客户端操作 ID 会被刻意忽略，
     * 因此数据库提交成功但 Redis XDEL 前发生崩溃时，后续回放仍保持幂等。
     */
    int insertBatchIgnoringDuplicates(@Param("logs") List<DocumentOpLogDO> logs);

    /** 读取指定文档在日志位点之后的有序更新批次。 */
    List<DocumentOpLogDO> selectByDocumentIdAfterId(@Param("documentId") Long documentId,
                                                    @Param("afterId") Long afterId,
                                                    @Param("limit") Integer limit);

    /** 统计指定文档在日志位点之后尚未被快照覆盖的操作数。 */
    long countByDocumentIdAfterId(@Param("documentId") Long documentId, @Param("afterId") Long afterId);

    /** 删除已被新快照指针覆盖的日志前缀。 */
    int deleteByDocumentIdThroughId(@Param("documentId") Long documentId,
                                    @Param("throughId") Long throughId);
}
