package com.jacolp.system.application.event;

import com.jacolp.common.messaging.event.StorageReleasedEvent;
import com.jacolp.system.api.quota.UserQuotaApi;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class StorageReleasedEventHandler {
    public static final String CONSUMER_NAME = "system.storage-released";

    private final UserQuotaApi userQuotaApi;

    public StorageReleasedEventHandler(UserQuotaApi userQuotaApi) {
        this.userQuotaApi = userQuotaApi;
    }

    public void apply(List<StorageReleasedEvent> events) {
        events.forEach(event -> userQuotaApi.releaseStorage(event.userId(), event.releasedBytes()));
    }
}
