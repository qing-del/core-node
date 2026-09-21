package com.jacolp.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Canal Java Client 的连接和拉取配置；默认关闭，避免普通开发启动时连接 Canal。 */
@ConfigurationProperties(prefix = "jacolp.document.file-index.canal")
public class FileIndexCanalProperties {

    private boolean enabled;
    private String host = "localhost";
    private int port = 11_111;
    private String destination = "example";
    private String username = "";
    private String password = "";
    private String filter = "personal_saas\\..*";
    private int batchSize = 100;
    private long pollDelayMs = 100L;

    /** 返回 Canal 增量监听开关。 */
    public boolean isEnabled() { return enabled; }
    /** 设置 Canal 增量监听开关；默认关闭。 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** 返回 Canal 服务地址。 */
    public String getHost() { return host; }
    /** 设置 Canal 服务地址。 */
    public void setHost(String host) { this.host = host; }
    /** 返回 Canal TCP 端口。 */
    public int getPort() { return port; }
    /** 设置 Canal TCP 端口。 */
    public void setPort(int port) { this.port = port; }
    /** 返回 destination 名称。 */
    public String getDestination() { return destination; }
    /** 设置 destination 名称。 */
    public void setDestination(String destination) { this.destination = destination; }
    /** 返回 Canal Client 用户名。 */
    public String getUsername() { return username; }
    /** 设置 Canal Client 用户名。 */
    public void setUsername(String username) { this.username = username; }
    /** 返回 Canal Client 密码。 */
    public String getPassword() { return password; }
    /** 设置 Canal Client 密码。 */
    public void setPassword(String password) { this.password = password; }
    /** 返回 Java Client 的显式订阅过滤规则。 */
    public String getFilter() { return filter; }
    /** 设置 Java Client 的显式订阅过滤规则。 */
    public void setFilter(String filter) { this.filter = filter; }
    /** 返回单次拉取的 Canal batch 大小。 */
    public int getBatchSize() { return batchSize; }
    /** 设置单次拉取的 Canal batch 大小。 */
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    /** 返回空 batch 轮询间隔。 */
    public long getPollDelayMs() { return pollDelayMs; }
    /** 设置空 batch 轮询间隔。 */
    public void setPollDelayMs(long pollDelayMs) { this.pollDelayMs = pollDelayMs; }
}
