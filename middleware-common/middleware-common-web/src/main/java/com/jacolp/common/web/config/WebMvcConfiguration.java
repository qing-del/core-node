package com.jacolp.common.web.config;

import com.jacolp.common.core.metrics.QpsCounter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Bean
    public QpsCounter qpsCounter() {
        return new QpsCounter();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("个人SaaS中台项目")
                .version("0.0.1")
                .description("个人SaaS中台项目接口文档"));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("admin 端接口")
                .packagesToScan("com.jacolp.controller.admin").build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder().group("user 端接口")
                .packagesToScan("com.jacolp.controller.user").build();
    }

    @Bean
    public GroupedOpenApi guestApi() {
        return GroupedOpenApi.builder().group("guest 端接口")
                .packagesToScan("com.jacolp.controller.guest").build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("Start setting up static resource mapping...");
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
