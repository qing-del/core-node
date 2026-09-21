package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code biz_document_relation} 中源文档和 resourceReference 的当前关系。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRelationDO implements Serializable {

    /** Java 序列化版本号，不对应数据库字段。 */
    private static final long serialVersionUID = 1L;

    /** 源文档 ID，与 refId 组成联合主键。 */
    private Long sourceDocumentId;
    /** 正文 resourceReference 节点的稳定 UUID，与 sourceDocumentId 组成联合主键。 */
    private String refId;
    /** 被引用资源节点 ID。 */
    private Long resourceNodeId;
    /** 软删除标记；false 表示当前关系有效。对应数据库 {@code is_delete}。 */
    private Boolean deleted;
}
