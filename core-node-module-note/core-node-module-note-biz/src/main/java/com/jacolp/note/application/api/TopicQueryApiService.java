package com.jacolp.note.application.api;

import com.jacolp.note.infrastructure.persistence.mapper.TopicMapper;
import com.jacolp.note.api.TopicQueryApi;
import org.springframework.stereotype.Service;

@Service
public class TopicQueryApiService implements TopicQueryApi {
    private final TopicMapper topicMapper;
    public TopicQueryApiService(TopicMapper topicMapper) { this.topicMapper = topicMapper; }
    @Override public boolean isOwnedBy(Long topicId, Long userId) {
        if (topicId == null || topicId <= 0 || userId == null || userId <= 0) return false;
        var topic = topicMapper.selectById(topicId);
        return topic != null && userId.equals(topic.getUserId());
    }
}
