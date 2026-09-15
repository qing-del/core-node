package com.jacolp.framework.oss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AliyunOSSOperatorTest {

    @Test
    void reportsClientFailuresInsteadOfReturningFalseSuccess() {
        AliyunOSSProperties properties = new AliyunOSSProperties();
        properties.setBucketName("bucket");
        AliyunOSSClient client = mock(AliyunOSSClient.class);
        OSS oss = mock(OSS.class);
        when(client.getOssClient()).thenReturn(oss);
        doThrow(new ClientException("network unavailable"))
                .when(oss).deleteObject("bucket", "image/7/a.png");

        assertThat(new AliyunOSSOperator(properties, client).delete("image/7/a.png")).isFalse();
    }
}
