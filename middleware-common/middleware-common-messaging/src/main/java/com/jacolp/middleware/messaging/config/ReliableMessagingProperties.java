package com.jacolp.middleware.messaging.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jacolp.messaging")
public class ReliableMessagingProperties {
    private boolean enabled = true;
    private int batchSize = 100;
    private int shardSize = 100;
    private int maxRetries = 8;
    private long pollDelayMs = 1000;
    private long claimSeconds = 60;
    private long confirmTimeoutMs = 10000;
    private long retryQueueDelayMs = 10000;
    private int maxPayloadBytes = 262144;
    private Duration initialBackoff = Duration.ofSeconds(2);
    private Duration maxBackoff = Duration.ofMinutes(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getShardSize() { return shardSize; }
    public void setShardSize(int shardSize) { this.shardSize = shardSize; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getPollDelayMs() { return pollDelayMs; }
    public void setPollDelayMs(long pollDelayMs) { this.pollDelayMs = pollDelayMs; }
    public long getClaimSeconds() { return claimSeconds; }
    public void setClaimSeconds(long claimSeconds) { this.claimSeconds = claimSeconds; }
    public long getConfirmTimeoutMs() { return confirmTimeoutMs; }
    public void setConfirmTimeoutMs(long confirmTimeoutMs) { this.confirmTimeoutMs = confirmTimeoutMs; }
    public long getRetryQueueDelayMs() { return retryQueueDelayMs; }
    public void setRetryQueueDelayMs(long retryQueueDelayMs) { this.retryQueueDelayMs = retryQueueDelayMs; }
    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public void setMaxPayloadBytes(int maxPayloadBytes) { this.maxPayloadBytes = maxPayloadBytes; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
}
