package com.jacolp.middleware.module.note.biz.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jacolp.note.infrastructure.cache.GuestCacheAspect;
import com.jacolp.note.infrastructure.cache.GuestCacheConstant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.jacolp.note.application.dto.note.PublicNoteQueryDTO;

class GuestCacheAspectTest {

    @Test
    void listKeyNormalizesKeywordAndUsesDefaultPagination() {
        PublicNoteQueryDTO query = new PublicNoteQueryDTO();
        query.setKeyword("  Java  ");
        query.setTopicId(9L);

        String key = ReflectionTestUtils.invokeMethod(new GuestCacheAspect(), "buildGuestNoteListKey",
                GuestCacheConstant.GUEST_NOTE_LIST_CACHE, new Object[] {query});

        assertEquals("guest-note-list:keyword=Java:topicId=9:pageNum=1:pageSize=15", key);
    }
}
