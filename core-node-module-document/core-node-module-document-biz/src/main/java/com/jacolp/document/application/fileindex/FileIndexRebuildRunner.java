package com.jacolp.document.application.fileindex;

import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 仅在显式开启时执行一次 file 全量重建；正常启动不会清空或写入投影。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "rebuild-on-start", havingValue = "true")
public class FileIndexRebuildRunner implements ApplicationRunner {

    private final FileIndexMappingInitializer mappingInitializer;
    private final FileIndexProjectionService projectionService;

    /** 保存 mapping 初始化和数据重建入口，保证重建前物理索引已存在。 */
    public FileIndexRebuildRunner(FileIndexMappingInitializer mappingInitializer,
                                  FileIndexProjectionService projectionService) {
        this.mappingInitializer = Objects.requireNonNull(mappingInitializer, "mappingInitializer must not be null");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService must not be null");
    }

    /** 执行一次可重复的全量投影重建。 */
    @Override
    public void run(ApplicationArguments args) {
        mappingInitializer.ensureInitialized();
        projectionService.rebuildAll();
    }
}
