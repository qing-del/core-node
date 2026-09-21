package com.jacolp.audio.service;

import com.jacolp.audio.persistence.dataobject.AudioTaskDO;

/** Publishes resource cleanup commands after an audio task is deleted. */
public interface AudioResourceDeletePublisher {

    void publish(AudioTaskDO task);
}
