package com.jacolp.note.infrastructure.web;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.constant.NoteConstant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Aspect
@Component
@Order(1)
public class NoteSizeLimitAspect {

    @Pointcut("@annotation(noteFileLimit)")
    public void noteFileLimitPointcut(NoteFileLimit noteFileLimit) {
    }

    @Around("noteFileLimitPointcut(noteFileLimit)")
    public Object checkNoteFileLimit(ProceedingJoinPoint joinPoint, NoteFileLimit noteFileLimit) throws Throwable {
        // 获取 file 参数
        MultipartFile file = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof MultipartFile) {
                file = (MultipartFile) arg;
                break;
            }
        }

        // 判断 file 是不是有效的文件
        if (file == null || file.isEmpty()) {
            throw new BaseException(NoteConstant.NOTE_FILE_EMPTY);
        }

        // 文件大小校验
        if (file.getSize() > noteFileLimit.maxBytes()) {
            throw new BaseException(NoteConstant.NOTE_FILE_TOO_LARGE);
        }

        // 文件格式校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(NoteConstant.ALLOWED_NOTE_FORMAT)) {
            throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
        }

        return joinPoint.proceed();
    }
}
