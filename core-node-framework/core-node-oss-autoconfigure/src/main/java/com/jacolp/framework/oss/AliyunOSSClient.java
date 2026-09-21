package com.jacolp.framework.oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyuncs.exceptions.ClientException;

public class AliyunOSSClient {
    OSS ossClient;
    String bucketName;

    public AliyunOSSClient(AliyunOSSProperties aliyunOSSProperties) throws ClientException {
        this.bucketName = aliyunOSSProperties.getBucketName();
        String endpoint = aliyunOSSProperties.getEndpoint();
        String region = aliyunOSSProperties.getRegion();

        // 先获取环境变量中的访问凭证
        EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();

        // 创建OSSClient实例。
        // 当OSSClient实例不再使用时，调用shutdown方法以释放资源。
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        this.ossClient = OSSClientBuilder.create()
                .endpoint(endpoint)
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(region)
                .build();
    }

    public OSS getOssClient() {
        return ossClient;
    }

    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    public void keepLive() {
        ossClient.doesBucketExist(bucketName);
    }
}
