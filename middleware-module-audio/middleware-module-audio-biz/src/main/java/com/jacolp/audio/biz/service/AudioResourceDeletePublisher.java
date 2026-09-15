package com.jacolp.audio.biz.service;

import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;

/** Publishes resource cleanup commands after an audio task is deleted. */
public interface AudioResourceDeletePublisher {

    void publish(AudioTaskDO task);
}
