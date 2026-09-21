package com.jacolp.document.infrastructure.persistence.mapper;

import java.util.List;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkRedemptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 文档分享短链及其幂等兑换记录的持久化访问接口。 */
@Mapper
public interface DocumentShareLinkMapper {

    /** 按令牌摘要读取短链；原始令牌不会进入数据库。 */
    DocumentShareLinkDO selectByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /** 按文档读取全部短链，包含已取消、已过期和已耗尽记录。 */
    List<DocumentShareLinkDO> selectByDocumentId(@Param("documentId") Long documentId);

    /** 在兑换事务中锁定短链记录。 */
    DocumentShareLinkDO selectByIdForUpdate(@Param("id") Long id);

    /** 新增短链记录，并回填自增 ID。 */
    int insert(@Param("shareLink") DocumentShareLinkDO shareLink);

    /** 由生成者取消短链；重复取消按幂等处理。 */
    int revokeByIdAndCreator(@Param("id") Long id,
                             @Param("creatorUserId") Long creatorUserId);

    /** 只有仍有配额时才增加有效兑换次数。 */
    int incrementUsedCountIfAvailable(@Param("id") Long id);

    /** 查询指定短链和用户的兑换记录。 */
    DocumentShareLinkRedemptionDO selectRedemption(@Param("shareLinkId") Long shareLinkId,
                                                   @Param("userId") Long userId);

    /** 记录一次兑换；联合主键保证同一用户对同一短链幂等。 */
    int insertRedemption(@Param("redemption") DocumentShareLinkRedemptionDO redemption);
}
