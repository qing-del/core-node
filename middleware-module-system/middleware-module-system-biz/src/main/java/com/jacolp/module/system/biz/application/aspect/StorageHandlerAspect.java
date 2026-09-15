package com.jacolp.module.system.biz.application.aspect;

import com.jacolp.module.system.api.quota.StorageHandler;
import com.jacolp.constant.UserConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.application.dto.user.UserQuoteStorageDTO;
import com.jacolp.module.system.biz.application.dto.user.UserStorageHandlerDTO;
import com.jacolp.module.system.biz.domain.quota.UserQuotaPolicy;
import com.jacolp.result.Result;
import com.jacolp.module.system.api.quota.StorageOperationType;
import com.jacolp.module.system.biz.infrastructure.concurrent.LockOperator;

import com.jacolp.utils.RoleDataComputerUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.context.BaseContext;
import com.jacolp.module.system.api.quota.StorageUpdateContext;
import com.jacolp.module.note.api.NoteReadApi;
import com.jacolp.module.note.api.model.NoteSummary;
import com.jacolp.module.media.api.MediaFileApi;
import com.jacolp.module.media.api.MediaUsageApi;
import com.jacolp.module.media.api.model.MediaFileSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 存储空间校验切面 - 配额校验 + 一致性保证 + 存储空间更新
 * <p>所有存储变更（上传/修改/删除/批量删除）统一经过此切面，保证缓存与实际一致。</p>
 * <p>Order=2，在 ImageLimitAspect 后执行。</p>
 */
@Aspect
@Component
@Order(2)
@Slf4j
public class StorageHandlerAspect {

    @Autowired private LockOperator lockOperator;

    @Autowired private UserMapper userMapper;
    @Autowired private NoteReadApi noteReadApi;
    @Autowired private MediaUsageApi mediaUsageApi;
    @Autowired private MediaFileApi mediaFileApi;

    @Pointcut("@annotation(storageHandler)")
    public void storageHandlerPointcut(StorageHandler storageHandler) {
    }

    /**
     * 存储空间校验与更新入口。
     * <p>根据操作类型路由到不同的处理策略：</p>
     * <p>- DELETE / BATCH_DELETE：先执行业务，再加锁更新存储</p>
     * <p>- UPLOAD / MODIFY：先一致性检查，再配额校验，执行业务后直接写回（不加二次查询）</p>
     */
    @Transactional(rollbackFor = Exception.class)
    @Around("storageHandlerPointcut(storageHandler)")
    public Object storageHandler(ProceedingJoinPoint joinPoint,
                                 StorageHandler storageHandler) throws Throwable {
        StorageOperationType opType = storageHandler.operationType();
        // 处理多个文件删除操作
        if (opType == StorageOperationType.BATCH_DELETE) {
            return handleBatchDelete(joinPoint);
        }
        // 处理单个文件删除操作
        if (opType == StorageOperationType.DELETE) {
            return handleSingleDelete(joinPoint);
        }

        // 处理单个文件上传/修改
        return handleSingleFileOperation(joinPoint, storageHandler);
    }

    // ======================== 单次删除策略 ========================

    /**
     * 处理用户单次删除。
     * <p>流程：加用户锁 → 执行业务 → 从 ThreadLocal 取文件大小 → 查用户表取缓存值 → 直接运算写回</p>
     * <p>删除只会减少空间，无需一致性检查，直接用缓存值减文件大小。</p>
     */
    private Object handleSingleDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = BaseContext.getCurrentId();
        String lockKey = getLockKey(userId);
        String owner = getLockOwner();

        boolean acquired = false;
        try {
            // 加用户锁，最大等待 300ms
            acquired = lockOperator.tryLock(lockKey, owner, 300);
            if (!acquired) {
                throw new BaseException("系统繁忙，请稍后！");
            }

            // 执行业务
            Object result = joinPoint.proceed();

            // 获取被删除文件大小
            syncStorageAfterDelete(userId);
            return result;
        } finally {
            if (acquired) {
                lockOperator.releaseLock(lockKey, owner);
            }
            StorageUpdateContext.clear();   // 清理 ThreadLocal
        }
    }

    /**
     * 删除后同步存储空间。
     * <p>从 ThreadLocal 取被删文件大小，查用户表取缓存值，直接运算后写回。</p>
     * @throws BaseException 如果 ThreadLocal 中不存在对应的上下文 会抛出异常
     */
    private void syncStorageAfterDelete(Long userId) {
        // 获取被删除文件大小（从 ThreadLocal 中获取）
        Map<Long, Long> userStorageMap = StorageUpdateContext.getStorageMap();
        Long fileSize = userStorageMap != null ? userStorageMap.get(userId) : null;
        if (fileSize == null) {
            log.error("Failed to get file size from ThreadLocal");
            throw new BaseException("未知错误");
        }

        long deltaBytes = -fileSize;
        applyDeltaUpdate(userId, deltaBytes, null);
    }

    // ======================== 批量删除策略 ========================

    /**
     * 处理管理员批量删除。
     * <p>deleteImages() 通过 ThreadLocal 传递用户存储变更 Map，</p>
     * <p>本方法在业务执行后获取该 Map，批量加锁并更新存储。</p>
     */
    private Object handleBatchDelete(ProceedingJoinPoint joinPoint) throws Throwable {

        Object result;
        String owner = getLockOwner();
        List<String> acquiredKeys = null;

        try {
            result = joinPoint.proceed();
            // 尝试从上下文中获取 文件大小 Map
            Map<Long, Long> userStorageMap = StorageUpdateContext.getStorageMap();
            if (userStorageMap == null || userStorageMap.isEmpty()) {
                log.error("Failed to get file size from ThreadLocal");
                throw new BaseException("未知错误");
            }

            List<String> lockKeys = userStorageMap.keySet().stream()
                    .map(this::getLockKey)
                    .collect(Collectors.toList());

            acquiredKeys = lockOperator.tryLockBatch(lockKeys, owner, 1000);
            if (acquiredKeys == null) {
                throw new BaseException("系统繁忙，请稍后重试");
            }
            // 批量更新用户存储
            batchUpdateUserStorage(userStorageMap);
        } finally {
            if (acquiredKeys != null) lockOperator.releaseBatch(acquiredKeys, owner);
            StorageUpdateContext.clear();   // 清理 ThreadLocal
        }

        return result;
    }

    // ======================== 单文件操作策略（上传/修改） ========================

    /**
     * 处理单文件操作（上传/修改）的配额校验与存储更新。
     * <p>流程：查用户表取 maxBytes → 查 note+image 两表算 actualUsedBytes →</p>
     * <p>配额校验 → 执行业务 → 基于 actualUsedBytes 直接运算写回（不再重新 sum）</p>
     * <p>全程以 actualUsedBytes（实际值）为基准，配额判断准确。</p>
     */
    private Object handleSingleFileOperation(ProceedingJoinPoint joinPoint,
                                              StorageHandler annotation) throws Throwable {
        MultipartFile file = extractMultipartFile(joinPoint);
        if (file == null || file.isEmpty()) {
            return joinPoint.proceed();
        }

        Long userId = BaseContext.getCurrentId();
        long requiredBytes = resolveRequiredBytes(joinPoint, annotation, file);

        String lockKey = getLockKey(userId);
        String owner = getLockOwner();
        boolean acquired = false;
        try {
            // 0. 加用户锁，最大等待 300ms
            acquired = lockOperator.tryLock(lockKey, owner, 300);
            if (!acquired) {
                throw new BaseException("系统繁忙，请稍后！");
            }
            // 1. 查用户表获取最大配额
            CachedStorageInfo cachedInfo = loadAndSyncCachedStorageInfo(userId);

            // 2. 查 note + image 两表算实际使用量（一致性检查）
            long actualUsedBytes = computeActualUsedStorage(userId);

            // 3. 配额校验（基于实际值）
            UserQuotaPolicy.StorageDecision decision = UserQuotaPolicy.storageDecision(
                    actualUsedBytes, requiredBytes, cachedInfo.maxBytes());
            if (decision.exceedsLimit()) {
                throw new BaseException(UserConstant.MAX_STORAGE_LIMIT);
            }

            // 4. 执行业务逻辑
            Object result = joinPoint.proceed();

            // 5. 操作后写回（基于 actualUsedBytes 直接运算，不再用缓存值，不再重新 sum）
            syncStorageAfterUploadOrModify(userId, result, actualUsedBytes,
                    cachedInfo.maxBytes(), requiredBytes);

            return result;
        } finally {
            if (acquired) {
                lockOperator.releaseLock(lockKey, owner);
            }
            StorageUpdateContext.clear();   // 清理 ThreadLocal
        }
    }

    /**
     * 从用户表加载缓存的存储信息（仅用于获取 maxBytes）。
     * <p>- 如果不符合会自动同步数据库（单次网络IO）</p>
     * @return 返回该用户的最大存储额度
     */
    private CachedStorageInfo loadAndSyncCachedStorageInfo(Long userId) {
        // 尝试从用户表中获取存储信息
        UserQuoteStorageDTO quote = userMapper.selectQuoteStorageById(userId);
        if (quote == null) {
            throw new BaseException(UserConstant.NOT_FOUND_USER);
        }

        // 检查一下 maxStorageBytes 是否正常有确定值，没有就需要去查角色表来获取
        Long maxStorageBytes = quote.getMaxStorageBytes();
        if (maxStorageBytes == null) {
            maxStorageBytes = RoleDataComputerUtil.getStorage(quote.getRoleId());

            // 写入数据库中同步
            int affect = userMapper.updateMaxStorageById(userId, maxStorageBytes);
            if (affect <= 0) {
                log.error("Failed to update user storage after load, userId: {}", userId);
                throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
            }
        }
        // 返回
        return new CachedStorageInfo(maxStorageBytes);
    }

    /**
     * 上传/修改后，基于实际使用量直接运算并写回存储。
     * <p>操作后不再查 DB，直接用 actualUsedBytes 运算后写回。</p>
     * @param actualUsedBytes 操作前通过 note+image 两表 sum 得到的实际使用量
     * @param deltaBytes     UPLOAD=文件大小，MODIFY=差量（可为负数）
     */
    private void syncStorageAfterUploadOrModify(Long userId, Object result,
                                                long actualUsedBytes,
                                                long maxBytes,
                                                long deltaBytes) {
        // 仅业务成功时才更新；失败时保持原值
        boolean businessSucceeded = !(result instanceof Result r) || r.getCode() == Result.SUCCESS;
        if (!businessSucceeded) {
            return;
        }

        // 计算新的使用量
        UserQuotaPolicy.StorageDecision decision = UserQuotaPolicy.storageDecision(actualUsedBytes, deltaBytes, maxBytes);
        // 存储量溢出检查
        if (decision.underflows()) {
            log.error("Storage underflow, userId: {}", userId);
            throw new BaseException("存储量溢出！");
        }

        applyDeltaUpdate(userId, deltaBytes, maxBytes);
    }

    // ======================== 锁管理 ========================

    private String getLockKey(Long userId) {
        return "StorageLock:User:" + userId;
    }

    private String getLockOwner() {
        return Thread.currentThread().getName() + ":" + Thread.currentThread().getId();
    }

    // ======================== 批量存储更新 ========================

    /**
     * 批量更新用户存储空间。
     * <p>使用 MyBatis 批量查询 + 批量 upsert，将网络 IO 从 O(n) 降为 O(1)。</p>
     * <p>Map 中的值为待释放的空间量（正值），更新时从 currentUsed 中减去。</p>
     */
    private void batchUpdateUserStorage(Map<Long, Long> userStorageMap) {
        List<Long> userIds = new ArrayList<>(userStorageMap.keySet());
        int userNum = userMapper.countByIds(userIds);

        if (userNum != userIds.size()) {
            log.error("Some users not found, userIds: {}", userIds);
            throw new BaseException(UserConstant.NOT_FOUND_USER);
        }

        List<UserStorageHandlerDTO> updateList = userIds.stream()
                .map(userId -> new UserStorageHandlerDTO(userId, userStorageMap.get(userId)))
                .toList();

        // 批量 upsert
        if (!updateList.isEmpty()) {
            int count = userMapper.batchUpdateStorage(updateList);
            if (count < updateList.size()) {
                log.error("Failed to batch upsert storage, count: {}, expected: {}",
                        count, updateList.size());
            }
        }
    }

    /**
     * 基于增量更新存储，满足不超卖条件即可成功。
     */
    private void applyDeltaUpdate(Long userId, long deltaBytes, Long maxBytes) {
        if (UserQuotaPolicy.isNoStorageDelta(deltaBytes)) {
            return;
        }

        UserStorageHandlerDTO updateUser = new UserStorageHandlerDTO();
        updateUser.setId(userId);
        updateUser.setDeltaStorageBytes(deltaBytes);

        int count = userMapper.updateStorageById(updateUser);
        if (count > 0) {
            return;
        }

        if (maxBytes != null && deltaBytes > 0) {
            throw new BaseException(UserConstant.MAX_STORAGE_LIMIT);
        }

        log.error("Failed to update user storage with delta, userId: {}", userId);
        throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
    }

    // ======================== 工具方法 ========================

    /**
     * 从 DB 实时计算用户实际使用的存储空间（笔记 + 图片）。
     */
    private long computeActualUsedStorage(Long userId) {
        return noteReadApi.getUserStorageUsageBytes(userId)
                + mediaUsageApi.getUserStorageUsageBytes(userId);
    }

    /**
     * 解析操作所需的字节数。
     * <p>修改场景返回"新文件 - 旧文件"差量，普通上传返回完整文件大小。</p>
     */
    private long resolveRequiredBytes(ProceedingJoinPoint joinPoint,
                                      StorageHandler annotation,
                                      MultipartFile file) {
        long fileSize = file.getSize();
        if (annotation == null || annotation.operationType() != StorageOperationType.MODIFY) {
            return fileSize;
        }

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long resourceId) {
                NoteSummary note = noteReadApi.findNoteSummariesByIds(List.of(resourceId)).get(resourceId);
                if (note != null) {
                    return fileSize - note.storageBytes();
                }
                MediaFileSummary media = mediaFileApi.findByIds(List.of(resourceId)).get(resourceId);
                if (media != null) {
                    return fileSize - media.sizeBytes();
                }
            }
        }
        return fileSize;
    }

    /**
     * 从方法参数中提取 MultipartFile.
     */
    private MultipartFile extractMultipartFile(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof MultipartFile file) {
                return file;
            }
        }
        return null;
    }

    /**
     * 缓存的存储信息（仅用于获取 maxBytes）。
     */
    private record CachedStorageInfo(long maxBytes) {
    }
}

