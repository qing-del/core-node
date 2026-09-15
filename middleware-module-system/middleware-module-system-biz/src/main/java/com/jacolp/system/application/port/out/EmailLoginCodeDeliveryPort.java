package com.jacolp.system.application.port.out;

import com.jacolp.system.application.authorization.model.EmailLoginCodeDeliveryRequest;

/** Synchronous delivery boundary for one login email-code. */
public interface EmailLoginCodeDeliveryPort {

    void deliver(EmailLoginCodeDeliveryRequest request);
}
