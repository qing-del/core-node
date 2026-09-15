package com.jacolp.module.note.biz.infrastructure.web;

import com.jacolp.constant.NoteConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoteFileLimit {

    long maxBytes() default NoteConstant.MAX_NOTE_FILE_SIZE_BYTES;
}
