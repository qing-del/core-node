package com.jacolp.middleware.module.note.biz.application.api;

import com.jacolp.module.note.biz.application.api.TopicQueryApiService;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TopicDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TopicMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicQueryApiServiceTest {

    @Test
    void checksOwnershipUsingTopicPersistenceOnly() {
        TopicMapper mapper = mock(TopicMapper.class);
        TopicDO topic = new TopicDO();
        topic.setId(7L);
        topic.setUserId(11L);
        when(mapper.selectById(7L)).thenReturn(topic);

        TopicQueryApiService service = new TopicQueryApiService(mapper);

        assertTrue(service.isOwnedBy(7L, 11L));
        assertFalse(service.isOwnedBy(7L, 12L));
        verify(mapper, times(2)).selectById(7L);
    }

    @Test
    void rejectsInvalidIdentifiersWithoutQueryingPersistence() {
        TopicMapper mapper = mock(TopicMapper.class);
        TopicQueryApiService service = new TopicQueryApiService(mapper);

        assertFalse(service.isOwnedBy(null, 11L));
        assertFalse(service.isOwnedBy(7L, 0L));
        verify(mapper, never()).selectById(7L);
    }
}
