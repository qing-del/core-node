package com.jacolp.module.system.api.quota;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a storage-changing method for the compatibility storage quota aspect.
 * DELETE and BATCH_DELETE methods pass released bytes through
 * {@link StorageUpdateContext}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StorageHandler {

    StorageOperationType operationType();
}
