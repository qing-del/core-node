package com.jacolp.module.note.biz.application.api;

import com.jacolp.module.note.api.TopicQueryApi;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TopicMapper;
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
