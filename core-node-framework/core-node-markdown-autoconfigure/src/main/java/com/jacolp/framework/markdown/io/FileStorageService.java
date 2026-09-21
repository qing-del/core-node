package com.jacolp.framework.markdown.io;

import com.jacolp.framework.markdown.converter.MarkdownPublishService;

/**
 * 文件存储服务抽象接口。
 * <p>
 * 定义了将渲染完成的 HTML 页面持久化保存的统一规范。
 * 通过接口抽象，使上层业务逻辑（{@link MarkdownPublishService}）与底层存储实现完全解耦。
 * </p>
 *
 * <h3>扩展指南（写给未来的你）</h3>
 * <p>
 * 当前默认实现是写到本地 static 目录。如果以后你想把生成的 HTML 上传到云端，
 * 只需要新建一个实现类（比如 {@code AliyunOssStorageService}），实现这个接口的
 * {@link #save(String, String)} 方法，然后在构造 {@link MarkdownPublishService} 时
 * 传入新的实现即可——业务代码一行都不用改。这就是"依赖倒置原则（DIP）"的威力。
 * </p>
 *
 * <h3>Spring Boot 集成提示</h3>
 * <p>
 * 迁移到 Spring Boot 后，只需在实现类上加 {@code @Service} 注解，
 * Spring 会自动将它注入到 {@link MarkdownPublishService} 的构造函数中。
 * </p>
 */
public interface FileStorageService {

    /**
     * 将内容保存到指定的相对路径。
     * <p>
     * 实现类需自行处理目录创建、编码等底层细节。
     * 调用方只需关心"保存到哪个相对路径"和"保存什么内容"。
     *
     * @param relativePath 相对路径（如 {@code "java/并发编程.html"}），
     *                     不包含存储根目录前缀，由实现类内部拼接
     * @param content      要保存的文本内容（通常为完整的 HTML 页面字符串）
     */
    void save(String relativePath, String content);
}
