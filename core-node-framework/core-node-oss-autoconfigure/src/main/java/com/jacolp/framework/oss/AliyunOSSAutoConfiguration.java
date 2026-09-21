package com.jacolp.framework.oss;

import com.aliyuncs.exceptions.ClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云OSS的自动配置类
 */
@EnableConfigurationProperties(AliyunOSSProperties.class)
@Configuration
public class AliyunOSSAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AliyunOSSOperator aliyunOSSOperator(AliyunOSSProperties aliyunOSSProperties, AliyunOSSClient aliyunOSSClient) {
        return new AliyunOSSOperator(aliyunOSSProperties, aliyunOSSClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public AliyunOSSProperties aliyunOSSProperties() {
        return new AliyunOSSProperties();
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public AliyunOSSClient aliyunOSSClient(AliyunOSSProperties aliyunOSSProperties) throws ClientException {
        return new AliyunOSSClient(aliyunOSSProperties);
    }
}
