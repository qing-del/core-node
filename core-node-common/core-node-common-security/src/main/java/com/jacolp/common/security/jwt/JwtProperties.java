package com.jacolp.common.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jacolp.jwt")
@Data
public class JwtProperties {

    /**
     * 激活token令牌相关配置
     */
    private String activeSecretKey;
    private long activeTtl;
    private long activeCodeTtl;
    private String activeTokenName;
}
