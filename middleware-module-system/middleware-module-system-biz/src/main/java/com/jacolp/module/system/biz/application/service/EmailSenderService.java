package com.jacolp.module.system.biz.application.service;

import com.jacolp.module.system.biz.application.dto.email.EmailSendDTO;
import com.jacolp.module.system.biz.application.dto.email.EmailResultDTO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;

public interface EmailSenderService {
    /**
     * 发送激活邮件，返回生成的 token 供调试
     * <p>- 此接口没有设置速率限制</p>
     */
    String sendActivationEmail(UserDO user);

    /** 管理员发送自定义邮件 */
    EmailResultDTO sendCustomEmail(EmailSendDTO dto);

    /**
     * 底层发送 HTML 邮件
     * <p>- 此接口没有设置速率限制</p>
     */
    void sendHtmlMail(String to, String subject, String htmlContent);

    /**
     * 发送邮箱更改验证码到新邮箱
     * <p>- 此接口没有设置速率限制</p>
     */
    void sendEmailChangeCode(UserDO user, String newEmail);
}
