package com.jacolp.common.messaging.pulisher;

import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.UserProfileChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 用户资料变更事件发布器：供审核模块维护审核列表的用户名展示投影。 */
@Service
public class UserProfileEventPublisher {
    private final OutboxEventPublisher outbox;
    public UserProfileEventPublisher(OutboxEventPublisher outbox) { this.outbox = outbox; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UserProfileChangedEvent event) {
        outbox.publish(EventTypes.USER_PROFILE_CHANGED, EventTypes.USER_PROFILE_CHANGED,
                "USER", event.userId(), null, event);
    }
}
