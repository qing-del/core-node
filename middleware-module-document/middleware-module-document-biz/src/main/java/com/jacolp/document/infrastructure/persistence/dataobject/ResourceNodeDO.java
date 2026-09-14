package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code biz_resource_node} 中被文档正文引用的资源节点。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceNodeDO implements Serializable {

    /** Java 序列化版本号，不对应数据库字段。 */
    private static final long serialVersionUID = 1L;

    /** 资源节点自增主键。 */
    private Long id;
    /** 资源线上类型；来自 LINK targetType 的 {@code DOCUMENT}/{@code NOTE}/{@code IMAGE}。 */
    private String resourceType;
    /** 目标资源在其所属业务模块中的 ID。 */
    private Long targetId;
}
