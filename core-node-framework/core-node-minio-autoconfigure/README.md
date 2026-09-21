# core-node-minio-autoconfigure

为项目模块提供统一的 MinIO 自动配置和字节对象存储 API。业务模块通常只需引入
`core-node-minio-starter`，不需要自行创建 `MinioClient`、拼接 SDK 请求对象或重复实现限长读取。

```xml
<dependency>
    <groupId>com.jacolp</groupId>
    <artifactId>core-node-minio-starter</artifactId>
</dependency>
```

```yaml
jacolp:
  minio:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    # 是否使用 JVM/系统代理；访问 Tailscale 内网 MinIO 时通常保持 false
    use-system-proxy: false
    bucket:
      document: core-node-document
```

```java
@Service
class SnapshotService {
    private final MinioBucketResolver buckets;
    private final MinioObjectStorage storage;

    SnapshotService(MinioBucketResolver buckets, MinioObjectStorage storage) {
        this.buckets = buckets;
        this.storage = storage;
    }

    void write(byte[] state) {
        storage.write(buckets.requireBucket("document"), "document/7/state/a.bin", state,
                "application/octet-stream");
    }
}
```

`read(bucket, objectKey, maxBytes)` 会在读取期间执行上限校验；`write` 与 `delete` 使用调用方给定的精确 key。
该模块不会在启动时创建 Bucket，也不会规定对象命名、生命周期或业务元数据。需要流式上传、预签名 URL 等高级 SDK 能力时，仍可注入
自动配置的 `MinioClient`。
