package com.jacolp.framework.minio;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings and logical bucket names shared by modules using MinIO. */
@ConfigurationProperties(prefix = "jacolp.minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private boolean useSystemProxy;
    private Map<String, String> bucket = new LinkedHashMap<>();

    /** 返回 MinIO 服务端点。 */
    public String getEndpoint() { return endpoint; }
    /** 设置 MinIO 服务端点。 */
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    /** 返回访问密钥。 */
    public String getAccessKey() { return accessKey; }
    /** 设置访问密钥。 */
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    /** 返回秘密密钥。 */
    public String getSecretKey() { return secretKey; }
    /** 设置秘密密钥。 */
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    /** 返回 MinIO SDK 是否使用 JVM 的系统代理。 */
    public boolean isUseSystemProxy() { return useSystemProxy; }
    /** 设置 MinIO SDK 是否使用 JVM 的系统代理。 */
    public void setUseSystemProxy(boolean useSystemProxy) { this.useSystemProxy = useSystemProxy; }
    /** 返回逻辑桶名到物理桶名的映射。 */
    public Map<String, String> getBucket() { return bucket; }
    /** 复制逻辑桶名映射；null 表示没有桶映射。 */
    public void setBucket(Map<String, String> bucket) { this.bucket = bucket == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bucket); }

    /** 判断访问密钥和秘密密钥是否均已配置。 */
    public boolean hasCompleteCredentials() {
        return isNotBlank(accessKey) && isNotBlank(secretKey);
    }

    /** 判断字符串是否包含非空白内容。 */
    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
