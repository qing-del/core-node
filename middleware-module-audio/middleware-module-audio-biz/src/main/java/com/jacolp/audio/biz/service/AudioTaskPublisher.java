package com.jacolp.audio.biz.service;

import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;

public interface AudioTaskPublisher {

    void publish(AudioTaskDO task);
}
