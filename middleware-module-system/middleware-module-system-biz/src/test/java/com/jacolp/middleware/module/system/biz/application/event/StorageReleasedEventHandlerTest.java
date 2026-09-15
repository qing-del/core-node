package com.jacolp.middleware.module.system.biz.application.event;

import com.jacolp.middleware.messaging.event.StorageReleasedEvent;
import com.jacolp.module.system.api.quota.UserQuotaApi;
import com.jacolp.module.system.biz.application.event.StorageReleasedEventHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StorageReleasedEventHandlerTest {

    @Test
    void delegatesEachReleaseToTheSystemOwnedQuotaApi() {
        UserQuotaApi quotaApi = mock(UserQuotaApi.class);
        StorageReleasedEventHandler handler = new StorageReleasedEventHandler(quotaApi);

        handler.apply(List.of(new StorageReleasedEvent(7L, "IMAGE", "23", 512L)));

        verify(quotaApi).releaseStorage(7L, 512L);
    }
}
