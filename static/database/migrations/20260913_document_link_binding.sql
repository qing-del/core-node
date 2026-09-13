USE `personal_saas`;

-- Forward-only schema for the v0.6 document LINK Binding projection.
-- These tables intentionally do not create cross-module foreign keys or a
-- resource_type/target_id uniqueness constraint: a refId owns the relation
-- identity, while the resource node is merely the current target snapshot.
CREATE TABLE `biz_resource_node` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '跨模块资源节点ID',
    `resource_type` VARCHAR(32)  NOT NULL COMMENT '资源类型，例如 DOCUMENT',
    `target_id`     BIGINT       NOT NULL COMMENT '目标资源在所属业务模块中的ID',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档正文引用的跨模块资源节点';

CREATE TABLE `biz_document_relation` (
    `source_document_id` BIGINT      NOT NULL COMMENT '源文档ID',
    `ref_id`             CHAR(36)    NOT NULL COMMENT '正文resourceReference稳定UUID',
    `resource_node_id`   BIGINT      NOT NULL COMMENT '当前资源节点ID',
    `is_delete`          TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除标记(0:有效,1:删除)',
    PRIMARY KEY (`source_document_id`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档与正文资源引用节点的关系投影';
