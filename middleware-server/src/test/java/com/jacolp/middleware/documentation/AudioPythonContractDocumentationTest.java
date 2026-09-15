package com.jacolp.middleware.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jacolp.audio.biz.config.RabbitMqAudioTaskConfiguration;
import com.jacolp.audio.biz.constant.AudioConstant;
import com.jacolp.audio.biz.controller.common.AudioCallbackController;
import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.service.RabbitMqTaskPublisher;
import com.jacolp.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AudioPythonContractDocumentationTest {

    @Test
    void pythonDocumentsMatchImplementedCallbackAndQueueContract() throws Exception {
        Path root = locateRepositoryRoot();
        String primary = Files.readString(root.resolve(
                "static/document/python-audio-module/音频生成业务接口规范.md"));
        String migrationGuide = Files.readString(root.resolve(
                "static/document/python-audio-module/音频模块-Python服务器对接改造-20260730.md"));
        String currentGuide = Files.readString(root.resolve(
                "static/document/python-audio-module/音频模块-Python服务器对接文档-20260809.md"));
        String readme = Files.readString(root.resolve("README.md"));

        String basePath = AudioCallbackController.class
                .getAnnotation(RequestMapping.class).value()[0];
        String startPath = basePath + AudioCallbackController.class
                .getMethod("callbackStart", AudioCallbackStartDTO.class,
                        HttpServletRequest.class)
                .getAnnotation(PostMapping.class).value()[0];
        String finishPath = basePath + AudioCallbackController.class
                .getMethod("callbackFinish", AudioCallbackFinishDTO.class,
                        HttpServletRequest.class)
                .getAnnotation(PostMapping.class).value()[0];

        assertThat(Result.SUCCESS).isEqualTo(1);
        assertThat(AudioCallbackStartDTO.class.getDeclaredField("attempt")).isNotNull();
        assertThat(AudioCallbackFinishDTO.class.getDeclaredField("attempt")).isNotNull();

        for (String document : List.of(primary, migrationGuide, currentGuide)) {
            assertThat(document)
                    .contains(startPath)
                    .contains(finishPath)
                    .contains("X-Callback-Token")
                    .contains("\"code\": 1")
                    .contains("attempt")
                    .contains(AudioConstant.REDIS_STREAM_KEY)
                    .contains(RabbitMqTaskPublisher.EXCHANGE)
                    .contains(RabbitMqAudioTaskConfiguration.QUEUE)
                    .doesNotContain("/admin/audio/callback");
        }

        assertThat(migrationGuide)
                .contains("20260726_audio_task_retry_time.sql")
                .contains("20260730_refactor_mq.sql")
                .doesNotContain("20260730_audio_task_management.sql");
        assertThat(currentGuide)
                .contains("2026-08-09")
                .contains("stream:audio:deletions")
                .contains("audio.delete.exchange")
                .contains("audio.delete.queue")
                .contains("AudioSize")
                .contains("Java `Long`");
        assertThat(readme)
                .contains("redis-stream")
                .contains("rabbitmq")
                .contains("taskId / attempt / userId")
                .contains(startPath)
                .contains(finishPath)
                .contains("static/document/python-audio-module/音频模块-Python服务器对接文档-20260809.md");
    }

    private static Path locateRepositoryRoot() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("pom.xml"))
                    && Files.isDirectory(directory.resolve("static/document"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
