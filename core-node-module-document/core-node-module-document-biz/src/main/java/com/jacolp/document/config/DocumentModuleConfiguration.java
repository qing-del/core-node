package com.jacolp.document.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册文档模块配置；仅装配配置类，不会提前启动协作运行时。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DocumentProperties.class, YjsServiceProperties.class, FileIndexCanalProperties.class})
public class DocumentModuleConfiguration {
}
