package com.jacolp.module.note.biz.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.jacolp.context.PermissionContext;
import com.jacolp.module.note.biz.application.dto.topic.TopicAddDTO;
import com.jacolp.module.note.biz.application.dto.topic.TopicListDTO;
import com.jacolp.module.note.biz.application.dto.topic.TopicModifyDTO;
import com.jacolp.module.note.biz.application.dto.topic.UserTopicQueryDTO;
import com.jacolp.module.note.biz.application.vo.topic.TopicDetailVO;
import com.jacolp.module.note.biz.application.vo.topic.TopicListVO;
import com.jacolp.module.note.biz.application.vo.topic.TopicStatsVO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TopicDO;
import com.jacolp.module.note.biz.infrastructure.persistence.dto.TopicNoteCountDTO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TopicMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.ScopeConstant;
import com.jacolp.constant.TopicConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.result.PageResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Topic 领域服务实现。
 *
 * 约束：
 * 1. 所有查询和修改都按当前登录用户隔离（user_id）。
 * 2. 删除主题前必须校验该主题下无未删除笔记。
 */
@Service
@Slf4j
public class TopicServiceImpl implements TopicService {

    @Autowired private TopicMapper topicMapper;

    /**
     * 新增主题。
     */
    @Override
    public void addTopic(TopicAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String topicName = normalizeTopicName(dto.getTopicName());

        // 同一用户下主题名唯一
        TopicDO existed = topicMapper.selectByUserIdAndTopicName(userId, topicName);
        if (existed != null) {
            throw new BaseException(TopicConstant.TOPIC_ALREADY_EXISTS);
        }

        TopicDO topic = new TopicDO();
        BeanUtils.copyProperties(dto, topic);
        topic.setUserId(userId);
        topic.setParentId(validateParentId(userId, dto.getParentId()));

        // 未传排序时使用默认值，保证列表排序稳定
        Integer sortOrder = dto.getSortOrder();
        if (sortOrder == null) {
            topic.setSortOrder(TopicConstant.DEFAULT_SORT_ORDER);
        } else {
            topic.setSortOrder(sortOrder);
        }

        int count = topicMapper.insertTopic(topic);
        if (count <= 0) {
            throw new BaseException(TopicConstant.TOPIC_ADD_FAILED);
        }
    }

    /**
     * 修改主题（排序）。
     */
    @Override
    public void modifyTopic(TopicModifyDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 业务约束：主题不存在则不允许修改
        TopicDO existed = topicMapper.selectById(dto.getId());
        if (existed == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }
        // 检查是否有所属权
        if (!existed.getUserId().equals(userId)) {
            throw new BaseException(TopicConstant.TOPIC_NOT_OWNER);
        }

        if (dto.getParentId() != null) {
            existed.setParentId(validateParentId(userId, dto.getParentId(), existed.getId()));
        }

        if (dto.getSortOrder() != null) {
            existed.setSortOrder(dto.getSortOrder());
        }

        // 更新修改到 DB
        int count = topicMapper.updateTopic(existed);
        if (count <= 0) {
            throw new BaseException(TopicConstant.TOPIC_UPDATE_FAILED);
        }
    }

    /**
     * 通过 ID 查询主题详情。
     * <p>- 通过使用 {@link PermissionContext#isAdmin()} 来判断是否需要校验所有权</p>
     */
    @Override
    public TopicDetailVO getTopicById(Long id) {
        validateTopicId(id);

        TopicDO topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }

        // 如果不是管理员需要做权限检查
        if (!PermissionContext.isAdmin()
        && !topic.getUserId().equals(BaseContext.getCurrentId())) {
            throw new BaseException(TopicConstant.TOPIC_NOT_OWNER);
        }

        TopicDetailVO vo = new TopicDetailVO();
        BeanUtils.copyProperties(topic, vo);
        return vo;
    }

    /**
     * 分页查询主题列表。
     */
    @Override
    public PageResult listTopics(TopicListDTO dto) {
        if (dto == null) {
            dto = new TopicListDTO();
        }


        // PageHelper 必须在查询语句前调用
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<TopicListVO> records = topicMapper.listByCondition(dto.getUserId(), normalizeKeyword(dto.getKeyword()));
        PageInfo<TopicListVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public List<TopicListVO> listChildren(Long parentId) {
        Long userId = BaseContext.getCurrentId();
        if (parentId != null) {
            validateParentId(userId, parentId);
        }
        return topicMapper.listChildrenByParentId(userId, parentId, parentId == null);
    }

    @Override
    public List<TopicListVO> listChildrenByUserId(Long userId, Long parentId) {
        if (userId == null || userId <= 0) {
            throw new BaseException("无效的用户ID");
        }
        if (parentId != null) {
            validateParentId(userId, parentId);
        }
        return topicMapper.listChildrenByParentId(userId, parentId, parentId == null);
    }

    /**
     * 批量删除主题。
     * <ol>
     *     <il>- 先全量校验主题是否存在、是否可删</il>
     *     <il>- 校验全部通过后再执行删除，减少回滚成本</il>
     * </ol>
     * <p>- 通过 {@link PermissionContext#isAdmin()} 来判断是否需要进行所有权校验</p>
     * @param ids 主题 id 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTopics(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的主题 ID 列表不能为空");
        }

        // 对 ID 列表进行去重
        ids = ids.stream().distinct().collect(Collectors.toList());

        // 从数据库中查询
        List<TopicNoteCountDTO> deleteChecks = topicMapper.selectDeleteChecksByIds(ids);
        if (deleteChecks.size() != ids.size()) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }

        // 如果不是管理员调用，还需要保证删除的主题都是属于当前用户
        if (!PermissionContext.isAdmin()) {
            for (TopicNoteCountDTO topic : deleteChecks) {
                if (!topic.getUserId().equals(BaseContext.getCurrentId())) {
                    throw new BaseException(TopicConstant.TOPIC_NOT_OWNER);
                }
            }
        }

        // 业务约束：主题下有未删除笔记则不允许删除
        for (TopicNoteCountDTO topic : deleteChecks) {
            if (topic.getNoteCount() != null && topic.getNoteCount() > 0) {
                throw new BaseException(TopicConstant.TOPIC_DELETE_NOT_ALLOWED_PREFIX
                        + topic.getTopicName()
                        + TopicConstant.TOPIC_DELETE_NOT_ALLOWED_SUFFIX);
            }
        }

        int count = topicMapper.deleteByIds(new ArrayList<>(ids));
        if (count < ids.size()) {
            throw new BaseException(TopicConstant.TOPIC_DELETE_FAILED);
        }
    }

    /**
     * 用户端条件查询：当前用户自己的主题。
     */
    @Override
    public PageResult listUserTopics(UserTopicQueryDTO dto) {
        if (dto == null) {
            dto = new UserTopicQueryDTO();
        }
        Long userId = BaseContext.getCurrentId();
        boolean globalScope = ScopeConstant.SCOPE_GLOBAL.equals(dto.getScope());

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<TopicListVO> records = topicMapper.listByUserCondition(userId, normalizeKeyword(dto.getKeyword()), globalScope);
        PageInfo<TopicListVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 获取当前用户主题统计。
     */
    @Override
    public TopicStatsVO getUserTopicStats() {
        Long userId = BaseContext.getCurrentId();
        long topicCount = topicMapper.countByUserId(userId);
        long passedCount = topicCount;
        return new TopicStatsVO(topicCount, passedCount);
    }

    @Override
    public boolean topicValid(Long topicId) {
        if (topicId == null || topicId <= 0) {
            return false;
        }
        TopicDO topic = topicMapper.selectById(topicId);
        return topic != null && topic.getUserId().equals(BaseContext.getCurrentId());
    }

    /**
     * 获取主题名称并做必要的处理。
     * - 删除前后空格
     * - 长度不能超过 25 个字符
     */
    private String normalizeTopicName(String topicName) {
        if (topicName == null) {
            return null;
        }
        // 防止前后空格导致“看起来相同、实际上不同”的主题名
        return topicName.trim();
    }

    /**
     * 获取关键词并做必要的处理。
     * - 删除前后空格
     */
    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    /**
     * 校验主题 ID。
     * <p>- ID 不能为空</p>
     * <p>- ID 必须大于 0</p>
     */
    private void validateTopicId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(TopicConstant.TOPIC_ID_INVALID);
        }
    }

    private Long validateParentId(Long userId, Long parentId) {
        return validateParentId(userId, parentId, null);
    }

    private Long validateParentId(Long userId, Long parentId, Long currentTopicId) {
        if (parentId == null) {
            return null;
        }
        if (parentId <= 0) {
            throw new BaseException(TopicConstant.TOPIC_ID_INVALID);
        }
        if (parentId.equals(currentTopicId)) {
            throw new BaseException("父级目录不能是自己");
        }

        TopicDO parent = topicMapper.selectById(parentId);
        if (parent == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }
        if (!parent.getUserId().equals(userId)) {
            throw new BaseException(TopicConstant.TOPIC_NOT_OWNER);
        }
        if (currentTopicId != null && isDescendant(parentId, currentTopicId)) {
            throw new BaseException("父级目录不能是当前目录的子目录");
        }
        return parentId;
    }

    private boolean isDescendant(Long candidateParentId, Long currentTopicId) {
        Long cursor = candidateParentId;
        while (cursor != null) {
            if (cursor.equals(currentTopicId)) {
                return true;
            }
            TopicDO topic = topicMapper.selectById(cursor);
            cursor = topic == null ? null : topic.getParentId();
        }
        return false;
    }
}
