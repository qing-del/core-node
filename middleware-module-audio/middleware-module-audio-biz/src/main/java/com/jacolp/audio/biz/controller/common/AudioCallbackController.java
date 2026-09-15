package com.jacolp.audio.biz.controller.common;

import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.service.AudioTaskService;
import com.jacolp.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("AudioCallbackController")
@RequestMapping("/common/audio")
@Slf4j @CrossOrigin("*") @Validated
@Schema(description = "Common - 音频任务回调")
@Tag(name = "音频任务", description = "Python 引擎内网回调接口")
public class AudioCallbackController {
    @Autowired private AudioTaskService audioTaskService;
    @Value("${jacolp.audio.callback-token}") private String callbackToken;

    @GetMapping("/callback/test")
    @Operation(summary = "测试连接接口")
    public Result<String> callbackTest(HttpServletRequest request) {
        validateCallbackToken(request);
        return Result.success("Connected Successfully!");
    }

    @PostMapping("/callback/start")
    @Operation(summary = "回调 A：任务开始处理", description = "Python 消费者从 Redis 取出任务后调用，将任务状态从 PENDING 更新为 PROCESSING。（前端不用对接）")
    public Result<Boolean> callbackStart(@RequestBody @Valid AudioCallbackStartDTO dto, HttpServletRequest request) {
        validateCallbackToken(request);
        log.info("Audio callback start, taskId: {}", dto.getTaskId());
        return Result.success(audioTaskService.callbackStart(dto));
    }

    @PostMapping("/callback/finish")
    @Operation(summary = "回调 B：任务完成/失败", description = "Python 引擎生成完成或异常时调用，更新任务最终状态。返回 data=true 表示 DB 更新成功，false 时 Python 端应删除本地文件。（前端不用对接）")
    public Result<Boolean> callbackFinish(@RequestBody @Valid AudioCallbackFinishDTO dto, HttpServletRequest request) {
        validateCallbackToken(request);
        log.info("Audio callback finish, taskId: {}, status: {}", dto.getTaskId(), dto.getStatus());
        return Result.success(audioTaskService.callbackFinish(dto));
    }

    private void validateCallbackToken(HttpServletRequest request) {
        String token = request.getHeader("X-Callback-Token");
        if (!callbackToken.equals(token)) throw new com.jacolp.exception.AuthenticationException("无效的回调 Token");
    }
}
