package com.jacolp.module.media.biz.infrastructure.task;

import com.jacolp.framework.oss.AliyunOSSClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "jacolp.aliyun.oss", name = "enabled", havingValue = "true")
public class AliyunOSSClientKeepLiveTask {
    private final AliyunOSSClient client;
    public AliyunOSSClientKeepLiveTask(AliyunOSSClient client) { this.client = client; }
    @Scheduled(fixedRateString = "${jacolp.aliyun.oss.keep-live-time:45}", timeUnit = TimeUnit.SECONDS)
    public void keepLive() { client.keepLive(); }
}
