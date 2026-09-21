package com.jacolp.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 文档协作链路使用的运行时容量与时限配置。 */
@ConfigurationProperties(prefix = "jacolp.document")
public class DocumentProperties {
    private boolean enabled;
    private long closeDelayMs = 30_000L;
    private long sessionPresenceRefreshMs = 10_000L;
    private Websocket websocket = new Websocket();
    private FlushLog flushLog = new FlushLog();
    private Compact compact = new Compact();
    private Snapshot snapshot = new Snapshot();
    private NodeMigration nodeMigration = new NodeMigration();

    /** 返回文档模块总开关。 */
    public boolean isEnabled() { return enabled; }
    /** 设置文档模块总开关。 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** 返回 Room 延迟关闭时间。 */
    public long getCloseDelayMs() { return closeDelayMs; }
    /** 设置 Room 延迟关闭时间。 */
    public void setCloseDelayMs(long closeDelayMs) { this.closeDelayMs = closeDelayMs; }
    /** 返回 presence 租约刷新周期。 */
    public long getSessionPresenceRefreshMs() { return sessionPresenceRefreshMs; }
    /** 设置 presence 租约刷新周期。 */
    public void setSessionPresenceRefreshMs(long sessionPresenceRefreshMs) { this.sessionPresenceRefreshMs = sessionPresenceRefreshMs; }
    /** 返回 WebSocket 协议和容量配置。 */
    public Websocket getWebsocket() { return websocket; }
    /** 设置 WebSocket 配置；null 时恢复默认配置对象。 */
    public void setWebsocket(Websocket websocket) { this.websocket = websocket == null ? new Websocket() : websocket; }
    /** 返回 Redis Stream 刷盘配置。 */
    public FlushLog getFlushLog() { return flushLog; }
    /** 设置刷盘配置；null 时恢复默认配置对象。 */
    public void setFlushLog(FlushLog flushLog) { this.flushLog = flushLog == null ? new FlushLog() : flushLog; }
    /** 返回快照压缩配置。 */
    public Compact getCompact() { return compact; }
    /** 设置压缩配置；null 时恢复默认配置对象。 */
    public void setCompact(Compact compact) { this.compact = compact == null ? new Compact() : compact; }
    /** 返回快照大小配置。 */
    public Snapshot getSnapshot() { return snapshot; }
    /** 设置快照大小配置；null 时恢复默认配置对象。 */
    public void setSnapshot(Snapshot snapshot) { this.snapshot = snapshot == null ? new Snapshot() : snapshot; }
    /** 返回历史节点身份迁移配置。 */
    public NodeMigration getNodeMigration() { return nodeMigration; }
    /** 设置历史节点身份迁移配置；null 时恢复默认配置对象。 */
    public void setNodeMigration(NodeMigration nodeMigration) {
        this.nodeMigration = nodeMigration == null ? new NodeMigration() : nodeMigration;
    }

    public static class Websocket {
        private int protocolVersion = 1;
        private int maxUpdateBytes = 256 * 1024;
        private int maxRoomSessions = 50;
        private int maxSendQueueBytes = 4 * 1024 * 1024;

        /** 返回 WebSocket 二进制/控制协议版本。 */
        public int getProtocolVersion() { return protocolVersion; }
        /** 设置 WebSocket 协议版本。 */
        public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }
        /** 返回单次客户端更新的字节上限。 */
        public int getMaxUpdateBytes() { return maxUpdateBytes; }
        /** 设置单次客户端更新的字节上限。 */
        public void setMaxUpdateBytes(int maxUpdateBytes) { this.maxUpdateBytes = maxUpdateBytes; }
        /** 返回单个 Room 的本机连接数上限。 */
        public int getMaxRoomSessions() { return maxRoomSessions; }
        /** 设置单个 Room 的本机连接数上限。 */
        public void setMaxRoomSessions(int maxRoomSessions) { this.maxRoomSessions = maxRoomSessions; }
        /** 返回单个会话的出站队列字节上限。 */
        public int getMaxSendQueueBytes() { return maxSendQueueBytes; }
        /** 设置单个会话的出站队列字节上限。 */
        public void setMaxSendQueueBytes(int maxSendQueueBytes) { this.maxSendQueueBytes = maxSendQueueBytes; }
    }

    public static class FlushLog {
        private long delayMs = 2_000L;
        private int batchSize = 500;
        private int maxBatchBytes = 2 * 1024 * 1024;
        private long recoveryScanMs = 30_000L;

        /** 返回 FLUSH_LOG 去抖延迟。 */
        public long getDelayMs() { return delayMs; }
        /** 设置 FLUSH_LOG 去抖延迟。 */
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
        /** 返回单次刷盘读取的最大条数。 */
        public int getBatchSize() { return batchSize; }
        /** 设置单次刷盘读取的最大条数。 */
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        /** 返回单次刷盘的软字节上限。 */
        public int getMaxBatchBytes() { return maxBatchBytes; }
        /** 设置单次刷盘的软字节上限。 */
        public void setMaxBatchBytes(int maxBatchBytes) { this.maxBatchBytes = maxBatchBytes; }
        /** 返回待刷盘恢复扫描周期。 */
        public long getRecoveryScanMs() { return recoveryScanMs; }
        /** 设置待刷盘恢复扫描周期。 */
        public void setRecoveryScanMs(long recoveryScanMs) { this.recoveryScanMs = recoveryScanMs; }
    }

    public static class Compact {
        private long intervalMs = 20_000L;
        private int maxUnmergedOps = 200;
        private int maxUnmergedBytes = 1024 * 1024;

        /** 返回 COMPACT 延迟尝试间隔。 */
        public long getIntervalMs() { return intervalMs; }
        /** 设置 COMPACT 延迟尝试间隔。 */
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        /** 返回触发立即压缩的未合并操作数阈值。 */
        public int getMaxUnmergedOps() { return maxUnmergedOps; }
        /** 设置触发立即压缩的未合并操作数阈值。 */
        public void setMaxUnmergedOps(int maxUnmergedOps) { this.maxUnmergedOps = maxUnmergedOps; }
        /** 返回触发立即压缩的未合并字节阈值。 */
        public int getMaxUnmergedBytes() { return maxUnmergedBytes; }
        /** 设置触发立即压缩的未合并字节阈值。 */
        public void setMaxUnmergedBytes(int maxUnmergedBytes) { this.maxUnmergedBytes = maxUnmergedBytes; }
    }

    public static class Snapshot {
        private int warnBytes = 2 * 1024 * 1024;
        private int maxBytes = 10 * 1024 * 1024;

        /** 返回快照大小告警阈值。 */
        public int getWarnBytes() { return warnBytes; }
        /** 设置快照大小告警阈值。 */
        public void setWarnBytes(int warnBytes) { this.warnBytes = warnBytes; }
        /** 返回快照大小硬上限。 */
        public int getMaxBytes() { return maxBytes; }
        /** 设置快照大小硬上限。 */
        public void setMaxBytes(int maxBytes) { this.maxBytes = maxBytes; }
    }

    /** 一次性节点身份迁移的运行开关、分页和请求分批配置。 */
    public static class NodeMigration {
        private boolean runOnStart;
        private int pageSize = 100;
        private int maxUpdateBatchBytes = 512 * 1024;
        private int maxCasRetries = 3;

        /** 返回是否在当前实例启动后执行一次迁移。 */
        public boolean isRunOnStart() { return runOnStart; }
        /** 设置启动时迁移开关；默认关闭。 */
        public void setRunOnStart(boolean runOnStart) { this.runOnStart = runOnStart; }
        /** 返回单次扫描的文档数量。 */
        public int getPageSize() { return pageSize; }
        /** 设置单次扫描的文档数量。 */
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        /** 返回单次提交给 Yjs 服务的增量字节上限。 */
        public int getMaxUpdateBatchBytes() { return maxUpdateBatchBytes; }
        /** 设置单次提交给 Yjs 服务的增量字节上限。 */
        public void setMaxUpdateBatchBytes(int maxUpdateBatchBytes) { this.maxUpdateBatchBytes = maxUpdateBatchBytes; }
        /** 返回快照 CAS 失败后的最大重试次数。 */
        public int getMaxCasRetries() { return maxCasRetries; }
        /** 设置快照 CAS 失败后的最大重试次数。 */
        public void setMaxCasRetries(int maxCasRetries) { this.maxCasRetries = maxCasRetries; }
    }
}
