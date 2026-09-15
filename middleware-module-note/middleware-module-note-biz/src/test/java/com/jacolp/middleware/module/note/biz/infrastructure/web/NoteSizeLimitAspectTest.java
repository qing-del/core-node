package com.jacolp.middleware.module.note.biz.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jacolp.note.infrastructure.web.NoteFileLimit;
import com.jacolp.note.infrastructure.web.NoteSizeLimitAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class NoteSizeLimitAspectTest {

    @Test
    void acceptsMarkdownWithinDeclaredLimit() throws Throwable {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(5L);
        when(file.getOriginalFilename()).thenReturn("note.md");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[] {file});
        when(joinPoint.proceed()).thenReturn("ok");
        NoteFileLimit limit = mock(NoteFileLimit.class);
        when(limit.maxBytes()).thenReturn(5L);

        assertEquals("ok", new NoteSizeLimitAspect().checkNoteFileLimit(joinPoint, limit));
    }
}
