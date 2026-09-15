package com.jacolp.system.application.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonitorDataVO {
    private double cpuUsage;
    private long heapUsedMB;
    private long heapMaxMB;
    private int runnableThreads;
    private int blockedThreads;
    private int waitingThreads;
    private int qps;
    private long timestamp;
}
