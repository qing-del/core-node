package com.jacolp.framework.markdown.io;

import com.jacolp.framework.markdown.MarkdownProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 基于本地文件系统的存储服务实现。
 * <p>
 * 将 {@link com.jacolp.converter.MarkdownPublishService} 产出的 HTML 页面
 * 写入到 {@link MarkdownProperty#getOutputDir()} 指定的本地目录中。
 *
 * <h3>存储规则</h3>
 * <p>
 * 调用方传入的 {@code relativePath}（如 {@code "java/并发编程.html"}）
 * 会与 {@code outputDir} 拼接成最终的绝对路径：
 * 
 * <pre>{@code
 *   outputDir = E:/JavaProject/core-node/static/html
 *   relativePath = java/并发编程.html
 *   → 最终路径 = E:/JavaProject/core-node/static/html/java/并发编程.html
 * }</pre>
 * 
 * 若目标目录不存在，会自动递归创建。
 *
 * <h3>扩展说明（写给未来的你）</h3>
 * <p>
 * 本类是 {@link FileStorageService} 接口的本地实现。如果以后想把 HTML 上传到
 * 阿里云 OSS 或其他云端存储，只需要：
 * <ol>
 * <li>新建一个类（如 {@code AliyunOssStorageService}）实现
 * {@link FileStorageService}</li>
 * <li>在 {@link com.jacolp.MarkdownAutoConfiguration} 中替换 Bean 注册</li>
 * </ol>
 * 上层的 {@link com.jacolp.converter.MarkdownPublishService} 完全不需要改动。
 * </p>
 *
 * <h3>Spring Boot 装配方式</h3>
 * <p>
 * 本类通过 {@link com.jacolp.MarkdownAutoConfiguration} 中的 {@code @Bean} 方法注册，
 * 构造时自动注入 {@link MarkdownProperty} 来获取 {@code outputDir} 配置。
 * 使用 {@code @ConditionalOnMissingBean} 保护，用户可以自定义同类型 Bean 进行覆盖。
 * </p>
 */
public class LocalFileStorageService implements FileStorageService {

    /** HTML 输出的根目录，由 {@link MarkdownProperty#getOutputDir()} 提供 */
    private final Path outputDir;

    /**
     * 通过 {@link MarkdownProperty} 构造本地存储服务。
     * <p>
     * 从配置对象中读取 {@code outputDir}，将其解析为 {@link Path} 作为本地写入的基准路径。
     * 这是 Spring Boot AutoConfiguration 中使用的构造方式。
     *
     * @param property Markdown 配置属性（由 Spring 自动注入）
     */
    public LocalFileStorageService(MarkdownProperty property) {
        this(Path.of(property.getOutputDir()));
    }

    /**
     * 直接通过 {@link Path} 构造本地存储服务。
     * <p>
     * 适用于非 Spring 环境下的手动创建场景。
     *
     * @param outputDir HTML 输出的根目录
     */
    public LocalFileStorageService(Path outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * 将 HTML 内容保存到本地文件。
     * <p>
     * 写入流程：
     * <ol>
     * <li>将 {@code relativePath} 与 {@code outputDir} 拼接为绝对路径</li>
     * <li>对路径进行 {@link Path#normalize()} 规范化，消除 {@code ..} 等不安全片段</li>
     * <li>若父目录不存在，自动递归创建（{@link Files#createDirectories}）</li>
     * <li>以 UTF-8 编码写入文件内容</li>
     * </ol>
     *
     * @param relativePath 相对路径（如 {@code "java/并发编程.html"}）
     * @param content      完整的 HTML 页面字符串
     * @throws UncheckedIOException 当文件写入失败时抛出（包装了底层 {@link IOException}）
     */
    @Override
    public void save(String relativePath, String content) {
        Path outputPath = outputDir.resolve(relativePath).normalize();
        try {
            // 确保父目录链存在（如 html/java/ 目录），不存在则递归创建
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 以 UTF-8 编码写入 HTML 文件
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("Fail to save file: " + outputPath);
            throw new UncheckedIOException("导入文件失败： " + outputPath, exception);
        }
    }
}
