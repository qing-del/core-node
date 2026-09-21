package com.jacolp.framework.markdown;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.io.File;

/**
 * Markdown 模块配置属性。
 * <p>
 * 通过 {@code @ConfigurationProperties} 绑定 application.yaml 中以 {@code jacolp.markdown} 为前缀的配置项。
 * Spring Boot 启动时会自动将 YAML 中的值注入到对应字段，若 YAML 中没有配置则使用下方的默认值。
 *
 * <h3>YAML 配置示例（写在你的 application.yaml 中）</h3>
 * <pre>{@code
 * jacolp:
 *   markdown:
 *     root-dir: E:/JavaProject/core-node/static      # 根目录（一般不需要改）
 *     input-dir: E:/JavaProject/core-node/static/notes # Markdown 源文件目录
 *     output-dir: E:/JavaProject/core-node/static/html  # HTML 输出目录
 * }</pre>
 *
 * <h3>默认值约定</h3>
 * <p>
 * 所有路径均以 {@code System.getProperty("user.dir")}（即运行 JVM 时的工作目录）为基准：
 * <ul>
 *     <li>{@code rootDir} → {@code $USER_DIR/static}</li>
 *     <li>{@code inputDir} → {@code $USER_DIR/static/notes}</li>
 *     <li>{@code outputDir} → {@code $USER_DIR/static/html}</li>
 * </ul>
 * 在父项目 {@code core-node} 根目录下执行时，这些路径会自动指向
 * {@code core-node/static}、{@code core-node/static/notes}、{@code core-node/static/html}。
 * </p>
 *
 */
@ConfigurationProperties(prefix = "jacolp.markdown")
public class MarkdownProperty {

    /**
     * 运行时工作目录。
     * <p>
     * 在父项目 core-node 根目录启动时，此值即为 core-node 文件夹的绝对路径。
     * 例如：{@code E:/JavaProject/core-node}
     */
    private static final String USER_DIR = System.getProperty("user.dir");

    /**
     * 根目录：所有 Markdown 相关文件的总基准路径。
     * <p>
     * 默认值：{@code $USER_DIR/static}（如 {@code E:/JavaProject/core-node/static}）
     * <p>
     * 对应 YAML 配置项：{@code jacolp.markdown.root-dir}
     */
    private String rootDir = USER_DIR + File.separator + "static";

    /**
     * 输入目录：Markdown 源文件的存放位置。
     * <p>
     * 当您调用 {@code LocalMarkdownScanner.scanAndPublishAll()} 时，
     * 扫描器会递归遍历此目录下所有 {@code .md} 文件进行转换。
     * <p>
     * 默认值：{@code $USER_DIR/static/notes}
     * <p>
     * 对应 YAML 配置项：{@code jacolp.markdown.input-dir}
     */
    private String inputDir = USER_DIR + File.separator + "static" + File.separator + "notes";

    /**
     * 输出目录：生成的 HTML 文件的保存位置。
     * <p>
     * {@link com.jacolp.io.LocalFileStorageService} 会以此目录为根，
     * 按照原 Markdown 文件的相对路径结构来存放 HTML 文件。
     * <p>
     * 默认值：{@code $USER_DIR/static/html}
     * <p>
     * 对应 YAML 配置项：{@code jacolp.markdown.output-dir}
     */
    private String outputDir = USER_DIR + File.separator + "static" + File.separator + "html";

    // ==================== Getter / Setter ====================

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    // ==================== toString ====================

    /**
     * 方便日志打印，输出所有当前生效的路径配置。
     */
    @Override
    public String toString() {
        return "MarkdownProperty{" +
               "rootDir='" + rootDir + '\'' +
               ", inputDir='" + inputDir + '\'' +
               ", outputDir='" + outputDir + '\'' +
               '}';
    }
}
