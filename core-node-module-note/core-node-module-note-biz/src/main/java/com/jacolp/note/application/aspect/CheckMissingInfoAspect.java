package com.jacolp.note.application.aspect;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.application.annotation.CheckMissingInfo;
import com.jacolp.note.application.provider.NoteIdProvider;
import com.jacolp.note.application.service.NoteCoreService;
import com.jacolp.note.application.service.NoteRelationService;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.enums.NoteMissingInfoMask;
import com.jacolp.note.application.dto.note.NoteMissingInfoDTO;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
public class CheckMissingInfoAspect {

    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private NoteRelationService noteRelationService;
    @Autowired private NoteCoreService noteCoreService;

    @Pointcut("@annotation(checkMissingInfo)")
    public void checkMissingInfoPointcut(CheckMissingInfo checkMissingInfo) {
    }

    @Around("checkMissingInfoPointcut(checkMissingInfo)")
    public Object checkMissingInfo(ProceedingJoinPoint joinPoint, CheckMissingInfo checkMissingInfo) throws Throwable {
        boolean enableTransaction = checkMissingInfo.enableTransaction();
        // 检查是否启用事务
        if (enableTransaction) {
            return transactionTemplate.execute(status -> {
                try {
                    return run(joinPoint);
                } catch (Throwable e) {
                    status.setRollbackOnly();   // 标记回滚
                    throw new BaseException(e.getMessage());
                }
            });
        }

        return run(joinPoint);
    }

    private @NonNull Object run(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        NoteIdProvider noteIdProvider = (NoteIdProvider) result;
        Long noteId = noteIdProvider.getNoteId();
        NoteMissingInfoDTO noteMissingInfo = noteRelationService.computeNoteMissingInfo(noteId);
        int missingInfo = noteMissingInfo.getMissingMask();
        int missingCount = noteMissingInfo.getMissingCount();

        // 检查是否需要回写到 DB
        if (NoteMissingInfoMask.isComplete(missingInfo) && missingCount == 0) {
            NoteDO note = noteCoreService.getById(noteId);
            note.setMissingInfoMask(missingInfo);
            note.setMissingCount(missingCount);
            noteCoreService.update(note);
        }

        return result;
    }
}
