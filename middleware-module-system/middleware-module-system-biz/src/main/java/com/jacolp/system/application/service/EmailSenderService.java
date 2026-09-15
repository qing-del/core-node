package com.jacolp.system.application.service;

import com.jacolp.system.application.dto.email.EmailResultDTO;
import com.jacolp.system.application.dto.email.EmailSendDTO;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;

public interface EmailSenderService {
    /** Queues an activation email in the current database transaction. */
    String sendActivationEmail(UserDO user);

    /** Queues one independently retryable custom-email command per recipient. */
    EmailResultDTO sendCustomEmail(EmailSendDTO dto);

    /** Queues an email-change verification message in the current transaction. */
    void sendEmailChangeCode(UserDO user, String newEmail);
}
