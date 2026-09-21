# Canal Java Client 接入配置

## 当前环境

- Canal Server：`canal/canal-server:v1.1.8`
- Canal destination：`example`
- Canal TCP 端口：`11111`
- MySQL 数据库：`personal_saas`
- Canal 服务端过滤规则：`personal_saas[.].*`

> 注意：`example` 是 Canal 的 destination 名称，`personal_saas` 是 MySQL 数据库名，两者不是同一个概念。

## Maven 依赖

```xml
<dependency>
    <groupId>com.alibaba.otter</groupId>
    <artifactId>canal.client</artifactId>
    <version>1.1.8</version>
</dependency>
```

## Java 连接配置

### Java 应用和 Canal 在同一个 Docker Network

如果 Java 应用也加入 `dev-net`：

```java
CanalConnector connector = CanalConnectors.newSingleConnector(
        new InetSocketAddress("canal", 11111),
        "example",
        "",
        ""
);
```

这里的 `canal` 是 `docker-compose.yml` 中的 service name。

### Java 在本地 IDE / Docker 网络之外运行

使用部署 Canal 的服务器 IP：

```java
CanalConnector connector = CanalConnectors.newSingleConnector(
        new InetSocketAddress("服务器IP", 11111),
        "example",
        "",
        ""
);
```

前提是服务器已经允许访问 `11111`。

## 订阅范围

Canal Server 当前已经配置：

```yaml
canal.instance.filter.regex: 'personal_saas[.].*'
```

因此服务端已经只读取 `personal_saas` 库。

Java Client 可以继续显式限定：

```java
connector.subscribe("personal_saas\\..*");
```

## 最小连接流程

```java
CanalConnector connector = CanalConnectors.newSingleConnector(
        new InetSocketAddress("canal", 11111),
        "example",
        "",
        ""
);

try {
    connector.connect();
    connector.subscribe("personal_saas\\..*");
    connector.rollback();

    // 后续读取 Message / Entry
} finally {
    connector.disconnect();
}
```

## 开发时重点注意

1. destination 必须写 `example`，不要写成 `personal_saas`。
2. MySQL 库名是 `personal_saas`，对应过滤/订阅表达式 `personal_saas\\..*`。
3. Java 在 `dev-net` 内连接 `canal:11111`；网络外连接服务器 IP + `11111`。
4. Java Canal Client 的用户名/密码不是 MySQL 的 `dev` / 数据库密码。当前 Canal Server 没有额外开启客户端认证时保持空字符串即可。
5. 当前 Canal Server 和 Java Client 建议都使用 `1.1.8`。
6. `11111` 才是 Java Client 连接 Canal Server 使用的 TCP 端口。

## 当前关系

```text
MySQL
└── database: personal_saas
       │
       │ Binlog
       ↓
Canal Server
└── destination: example
       │
       │ TCP :11111
       ↓
Java Canal Client
```
