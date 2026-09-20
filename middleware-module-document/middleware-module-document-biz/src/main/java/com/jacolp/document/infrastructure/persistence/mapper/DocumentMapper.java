package com.jacolp.document.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/** 将文档元数据、快照指针和所有者/授权条件映射到 {@code biz_document} SQL。 */
public interface DocumentMapper {

    /** 插入一条文档元数据记录，并回填自增主键。 */
    int insert(DocumentDO document);

    /** 按主键读取文档，包括已删除记录供内部状态判断。 */
    DocumentDO selectById(@Param("id") Long id);

    /** 按主键读取活跃文档；资源级访问由调用方统一校验。 */
    DocumentDO selectActiveById(@Param("id") Long id);

    /** 批量读取正常文档，用于管理员在对象存储写操作前完成全量前置校验。 */
    List<DocumentDO> selectActiveByIds(@Param("ids") List<Long> ids);

    /** 按主键游标读取全部文档，供一次性历史迁移稳定遍历。 */
    List<DocumentDO> selectByIdAfter(@Param("afterId") Long afterId, @Param("limit") Integer limit);

    /** 列出指定所有者的全部活跃文档。 */
    List<DocumentDO> listActiveByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    /** 列出当前用户作为所有者或被授予有效 READ/WRITE 权限的活跃文档。 */
    List<DocumentDO> listActiveVisibleByUserId(@Param("userId") Long userId);

    /** 供管理员按所有者分组列出全部正常文档；排序保证树形响应稳定。 */
    List<DocumentDO> listActiveForAdmin();

    /** 在文档仍活跃且调用方仍拥有写权限时更新最后修改审计字段。 */
    int updateLastModificationIfActive(@Param("id") Long id, @Param("userId") Long userId,
                                       @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                       @Param("lastModifyUserId") Long lastModifyUserId);

    /** 在所有者范围内更新活跃文档标题和最后修改审计字段。 */
    int updateTitleIfActive(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId,
                            @Param("title") String title,
                            @Param("lastModifyTime") LocalDateTime lastModifyTime,
                            @Param("lastModifyUserId") Long lastModifyUserId);

    /** 仅当前快照仍对应预期日志位点时，才把读取入口切换到新快照对象。 */
    int updateSnapshotPointerIfPersistedLogId(@Param("id") Long id,
                                              @Param("expectedPersistedLogId") Long expectedPersistedLogId,
                                              @Param("contentObjectKey") String contentObjectKey,
                                              @Param("persistedLogId") Long persistedLogId);

    /** 在所有者范围内把文档标记为删除，不物理清理正文和操作日志。 */
    int softDeleteByIdAndOwnerUserId(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId,
                                     @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                     @Param("lastModifyUserId") Long lastModifyUserId);
}
