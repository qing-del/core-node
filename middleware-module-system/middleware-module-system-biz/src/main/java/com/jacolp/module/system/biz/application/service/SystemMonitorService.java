package com.jacolp.module.system.biz.application.service;

import com.jacolp.module.system.biz.application.vo.MonitorDataVO;

public interface SystemMonitorService {
    /**
     * 收集服务器的资源信息
     * @return
     */
    MonitorDataVO collect();

    /**
     * 重置QPS计数器
     */
    void resetQPSCounter();
}
