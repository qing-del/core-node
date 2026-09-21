package com.jacolp.framework.elasticsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings and logical index names shared by Elasticsearch consumers. */
@ConfigurationProperties(prefix = "jacolp.elasticsearch")
public class ElasticsearchProperties {
    private List<String> uris = new ArrayList<>();
    private String username;
    private String password;
    private Map<String, String> index = new LinkedHashMap<>();

    /** 返回当前绑定的 Elasticsearch 节点 URI 列表；setter 会复制外部输入。 */
    public List<String> getUris() { return uris; }
    /** 复制节点 URI 配置；null 表示没有配置节点。 */
    public void setUris(List<String> uris) { this.uris = uris == null ? new ArrayList<>() : new ArrayList<>(uris); }
    /** 返回基本认证用户名。 */
    public String getUsername() { return username; }
    /** 设置基本认证用户名。 */
    public void setUsername(String username) { this.username = username; }
    /** 返回基本认证密码。 */
    public String getPassword() { return password; }
    /** 设置基本认证密码。 */
    public void setPassword(String password) { this.password = password; }
    /** 返回逻辑索引到物理索引的映射；setter 会复制外部输入。 */
    public Map<String, String> getIndex() { return index; }
    /** 复制逻辑索引映射；null 表示没有索引映射。 */
    public void setIndex(Map<String, String> index) { this.index = index == null ? new LinkedHashMap<>() : new LinkedHashMap<>(index); }

    /** 判断用户名和密码是否均已配置。 */
    public boolean hasCompleteCredentials() {
        return isNotBlank(username) && isNotBlank(password);
    }

    /** 判断是否只配置了用户名或密码中的一项。 */
    public boolean hasPartialCredentials() {
        return isNotBlank(username) != isNotBlank(password);
    }

    /** 统一判断配置字符串是否包含非空白内容。 */
    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
