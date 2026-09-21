package com.jacolp.document.infrastructure.persistence.mapper;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 查询文档与用户之间当前生效的直接授权关系。 */
@Mapper
public interface DocumentUserMappingMapper {

    /** 查询指定文档的全部授权记录，包含已撤销记录。 */
    List<DocumentUserMappingDO> selectByDocumentId(@Param("documentId") Long documentId);

    /** 批量读取多个文档当前生效的 READ/WRITE 授权，供 file 全量重建一次性组装可见用户。 */
    List<DocumentUserMappingDO> selectEnabledReadableByDocumentIds(@Param("documentIds") List<Long> documentIds);

    /** 查询指定文档用户的授权记录，不过滤 enabled。 */
    DocumentUserMappingDO selectByDocumentIdAndUserId(@Param("documentId") Long documentId,
                                                       @Param("userId") Long userId);

    /** 查询一个用户对指定文档的生效授权；不存在或已撤销时返回 {@code null}。 */
    DocumentUserMappingDO selectEnabledByDocumentIdAndUserId(@Param("documentId") Long documentId,
                                                              @Param("userId") Long userId);

    /** 仅在文档仍由指定所有者拥有且未删除时新增或更新授权。 */
    int upsertByDocumentOwner(@Param("mapping") DocumentUserMappingDO mapping,
                              @Param("ownerUserId") Long ownerUserId);

    /** 仅在文档仍由指定所有者拥有且未删除时撤销授权。 */
    int disableByDocumentOwner(@Param("documentId") Long documentId,
                               @Param("userId") Long userId,
                               @Param("ownerUserId") Long ownerUserId);
}
