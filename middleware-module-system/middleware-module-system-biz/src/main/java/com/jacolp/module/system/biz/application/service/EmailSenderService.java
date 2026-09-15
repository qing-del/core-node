package com.jacolp.module.system.biz.application.service;

import com.jacolp.module.system.biz.application.dto.email.EmailSendDTO;
import com.jacolp.module.system.biz.application.dto.email.EmailResultDTO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;

public interface EmailSenderService {
    /** Queues an activation email in the current database transaction. */
    String sendActivationEmail(UserDO user);

    /** Queues one independently retryable custom-email command per recipient. */
    EmailResultDTO sendCustomEmail(EmailSendDTO dto);

    /** Queues an email-change verification message in the current transaction. */
    void sendEmailChangeCode(UserDO user, String newEmail);
}
