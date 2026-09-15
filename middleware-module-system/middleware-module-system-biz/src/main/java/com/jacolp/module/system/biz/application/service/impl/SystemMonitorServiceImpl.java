package com.jacolp.module.system.biz.application.service.impl;

import com.jacolp.middleware.common.core.metrics.QpsCounter;
import com.jacolp.module.system.biz.application.vo.MonitorDataVO;
import com.jacolp.module.system.biz.application.service.SystemMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Slf4j
@Service
public class SystemMonitorServiceImpl implements SystemMonitorService {

    private record ThreadStatusCollectionResult(int runnable, int blocked, int waiting) {
    }

    private static final long COLLECT_INTERVAL_MS = 5000;
    private static long nextCollectTime = 0;
    private static double cpuUsage;
    private static ThreadStatusCollectionResult threadStatus;

    private final QpsCounter qpsCounter;
    private final SystemInfo systemInfo;
    private final MemoryMXBean memoryMXBean;

    private final ThreadMXBean threadMXBean;

    public SystemMonitorServiceImpl(QpsCounter qpsCounter) {
        this.qpsCounter = qpsCounter;
        this.systemInfo = new SystemInfo();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    public MonitorDataVO collect() {

        if (System.currentTimeMillis() >= nextCollectTime) {
            cpuUsage = collectCpuUsage();
            threadStatus = getThreadStatusCollectionResult();
            nextCollectTime = nextCollectTime();
        }

        long heapUsedMB = memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMaxMB = memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        int qps = qpsCounter.getAndReset();

        return MonitorDataVO.builder()
                .cpuUsage(cpuUsage)
                .heapUsedMB(heapUsedMB)
                .heapMaxMB(heapMaxMB)
                .runnableThreads(threadStatus.runnable())
                .blockedThreads(threadStatus.blocked())
                .waitingThreads(threadStatus.waiting())
                .qps(qps)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public void resetQPSCounter() {
        qpsCounter.getAndReset();
    }

    /**
     * 获取线程状态
     * <p>- 每 {@link #COLLECT_INTERVAL_MS} ms 执行一次</p>
     * @return 线程状态
     */
    private @NonNull ThreadStatusCollectionResult getThreadStatusCollectionResult() {
        int runnable = 0;
        int blocked = 0;
        int waiting = 0;

        long[] threadIds = threadMXBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadIds, 0);

        for (ThreadInfo info : threadInfos) {
            if (info == null) continue;
            Thread.State state = info.getThreadState();
            switch (state) {
                case RUNNABLE -> runnable++;
                case BLOCKED -> blocked++;
                case WAITING, TIMED_WAITING -> waiting++;
            }
        }
        return new ThreadStatusCollectionResult(runnable, blocked, waiting);
    }

    /**
     * 收集 CPU 的使用率
     * <p>- 每 {@link #COLLECT_INTERVAL_MS} ms 执行一次</p>
     * @return CPU 使用率
     */
    private double collectCpuUsage() {
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        double load = processor.getSystemCpuLoad(1000);
        return Math.max(0.0, load * 100.0);
    }

    /**
     * 下次采集时间
     * @return 下次采集时间
     */
    private static long nextCollectTime() {
        return System.currentTimeMillis() + COLLECT_INTERVAL_MS;
    }
}
