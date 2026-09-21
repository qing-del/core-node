package com.jacolp.audio.service;

import com.jacolp.audio.persistence.dataobject.AudioTaskDO;

public interface AudioTaskPublisher {

    void publish(AudioTaskDO task);
}
