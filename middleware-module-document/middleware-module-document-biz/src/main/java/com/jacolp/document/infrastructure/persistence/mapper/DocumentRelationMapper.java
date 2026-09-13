package com.jacolp.document.infrastructure.persistence.mapper;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentRelationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 持久化源文档与正文 resourceReference 的软删除关系。 */
@Mapper
public interface DocumentRelationMapper {

    /** 读取指定源文档/refId 的关系，包括已软删除关系。 */
    DocumentRelationDO selectBySourceDocumentIdAndRefId(@Param("sourceDocumentId") Long sourceDocumentId,
                                                        @Param("refId") String refId);

    /** 创建一条当前有效的文档关系。 */
    int insert(DocumentRelationDO relation);

    /** 恢复关系并切换到当前 resource node。 */
    int activateBySourceDocumentIdAndRefId(@Param("sourceDocumentId") Long sourceDocumentId,
                                           @Param("refId") String refId,
                                           @Param("resourceNodeId") Long resourceNodeId);

    /** 将当前关系标记为软删除。 */
    int softDeleteBySourceDocumentIdAndRefId(@Param("sourceDocumentId") Long sourceDocumentId,
                                             @Param("refId") String refId);
}
