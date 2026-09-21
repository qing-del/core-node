package com.jacolp.audit.api;

/**
 * 面向其他业务模块的审核申请契约。
 *
 * <p>该接口只管理审核申请本身；业务对象的所有权、状态变更和审核结果回写
 * 由所属模块的应用服务处理。</p>
 */
public interface AuditApplicationApi {

    /**
     * 检查目标是否已经存在待审核申请。
     */
    boolean hasPendingApplication(PendingAuditApplicationQuery query);

    /**
     * 创建审核申请。
     */
    AuditApplicationResult createApplication(CreateAuditApplicationCommand command);

    /**
     * 撤销目标的待审核申请。
     */
    CancelAuditApplicationResult cancelApplication(CancelAuditApplicationCommand command);
}
