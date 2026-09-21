package com.jacolp.framework.markdown;

import com.jacolp.framework.markdown.converter.MarkdownHtmlEngine;
import com.jacolp.framework.markdown.converter.MarkdownPlugin;
import com.jacolp.framework.markdown.converter.MarkdownPublishService;
import com.jacolp.framework.markdown.io.FileStorageService;
import com.jacolp.framework.markdown.io.LocalFileStorageService;
import com.jacolp.framework.markdown.io.LocalMarkdownScanner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flexmark Markdown 模块的 Spring Boot 自动配置类。
 * <p>
 * 该类负责将所有核心组件注册为 Spring Bean，使用者只需引入 Starter 依赖即可开箱即用。
 * 每个 Bean 都使用了 {@code @ConditionalOnMissingBean} 保护——如果你在自己的项目中
 * 手动注册了同类型的 Bean（比如换了一个 OSS 存储实现），这里的默认实现会自动让位。
 *
 * <h3>自动装配的组件清单</h3>
 * <ul>
 * <li>{@link MarkdownProperty} — 配置属性（绑定 {@code jacolp.markdown.*}）</li>
 * <li>{@link MarkdownHtmlEngine} — 核心解析引擎（纯文本 → 结构化 HTML）</li>
 * <li>{@link FileStorageService} — 存储服务（默认为本地文件系统）</li>
 * <li>{@link MarkdownPublishService} — 发布门面（编排引擎 + 套壳 + 存储）</li>
 * <li>{@link LocalMarkdownScanner} — 全量扫描调度器（手动触发，遍历 inputDir）</li>
 * </ul>
 *
 * <h3>如何替换默认存储？</h3>
 * <p>
 * 只需在你的 {@code @Configuration} 类中注册一个 {@link FileStorageService} 类型的 Bean：
 * <pre>{@code
 * @Bean
 * public FileStorageService fileStorageService() {
 *     return new AliyunOssStorageService(ossClient, bucketName, prefix);
 * }
 * }</pre>
 * 这里的 {@code localFileStorageService} 会因为 {@code @ConditionalOnMissingBean} 自动失效。
 * </p>
 */
@EnableConfigurationProperties(MarkdownProperty.class)
@Configuration
public class MarkdownAutoConfiguration {

//    /**
//     * 注册配置属性 Bean。
//     * <p>
//     * 虽然 {@code @EnableConfigurationProperties} 已经会注册一个，
//     * 但显式声明 Bean 可以让其他 Bean 方法直接通过参数注入，代码更清晰。
//     */
//    @Bean
//    @ConditionalOnMissingBean
//    public MarkdownProperty markdownProperty() {
//        return new MarkdownProperty();
//    }

    /**
     * 注册 Markdown 解析引擎。
     * <p>
     * 引擎是线程安全的，整个应用只需要一个实例。
     * 通过 {@link ObjectProvider} 自动发现容器中所有 {@link MarkdownPlugin} 类型的 Bean，
     * 将它们收集为有序列表并注入引擎的构造函数中。
     * <p>
     * 💡 想要自定义解析规则？只需在你的项目中编写一个实现 {@link MarkdownPlugin} 接口的类，
     * 打上 {@code @Component} 注解即可自动被引擎发现并加载——无需修改任何配置。
     *
     * @param pluginsProvider Spring 自动注入的插件提供者（按 {@code @Order} 排序）
     */
    @Bean
    @ConditionalOnMissingBean
    public MarkdownHtmlEngine markdownHtmlEngine(ObjectProvider<MarkdownPlugin> pluginsProvider) {
        return new MarkdownHtmlEngine(pluginsProvider.orderedStream().toList());
    }

    /**
     * 注册文件存储服务（默认：本地文件系统）。
     * <p>
     * 从 {@link MarkdownProperty} 中读取 {@code outputDir} 作为 HTML 输出根目录。
     * <p>
     * 💡 想换成阿里云 OSS？在你的项目中自定义一个 {@link FileStorageService} Bean 即可，
     * 这个默认的本地实现会自动让位（得益于 {@code @ConditionalOnMissingBean}）。
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageService fileStorageService(MarkdownProperty markdownProperty) {
        return new LocalFileStorageService(markdownProperty);
    }

    /**
     * 注册发布服务门面。
     * <p>
     * Spring 会自动将上面注册的 {@link MarkdownHtmlEngine} 和 {@link FileStorageService}
     * 通过构造器注入到 {@link MarkdownPublishService} 中。
     */
    @Bean
    @ConditionalOnMissingBean
    public MarkdownPublishService markdownPublishService(
            MarkdownHtmlEngine engine,
            FileStorageService storageService) {
        return new MarkdownPublishService(engine, storageService);
    }

    /**
     * 注册本地全量扫描调度器。
     * <p>
     * 该组件不会自动启动扫描！需要您在业务代码中手动注入并调用
     * {@link LocalMarkdownScanner#scanAndPublishAll()} 来触发。
     * <p>
     * 具体调用方式请参阅 {@link LocalMarkdownScanner} 类头部的 Javadoc 示例。
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalMarkdownScanner localMarkdownScanner(
            MarkdownPublishService publishService,
            MarkdownProperty property) {
        return new LocalMarkdownScanner(publishService, property);
    }
}
