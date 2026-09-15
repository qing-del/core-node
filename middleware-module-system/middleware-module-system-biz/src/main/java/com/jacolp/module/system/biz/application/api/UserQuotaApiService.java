package com.jacolp.module.system.biz.application.api;

import com.jacolp.constant.UserConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.module.system.api.quota.ConsumeQuotaCommand;
import com.jacolp.module.system.api.quota.ConsumeQuotaResult;
import com.jacolp.module.system.api.quota.QuotaSnapshot;
import com.jacolp.module.system.api.quota.QuotaType;
import com.jacolp.module.system.api.quota.UserQuotaApi;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.ApiDailyUsageDO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.ApiDailyUsageMapper;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.application.dto.user.UserQuoteStorageDTO;
import com.jacolp.module.system.biz.application.dto.user.UserStorageHandlerDTO;
import com.jacolp.module.system.biz.domain.quota.UserQuotaPolicy;
import com.jacolp.utils.RoleDataComputerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

/**
 * System-owned daily API and storage quota operations.
 */
@Service
public class UserQuotaApiService implements UserQuotaApi {

    private final UserMapper userMapper;
    private final ApiDailyUsageMapper apiDailyUsageMapper;

    public UserQuotaApiService(UserMapper userMapper, ApiDailyUsageMapper apiDailyUsageMapper) {
        this.userMapper = userMapper;
        this.apiDailyUsageMapper = apiDailyUsageMapper;
    }

    @Override
    public QuotaSnapshot getQuota(long userId, QuotaType quotaType, LocalDate quotaDate) {
        validateQuery(userId, quotaType, quotaDate);
        return switch (quotaType) {
            case DAILY_API_CALL -> dailyApiQuota(userId, quotaDate);
            case STORAGE_BYTES -> storageQuota(userId);
        };
    }

    @Override
    public ConsumeQuotaResult consume(ConsumeQuotaCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        QuotaSnapshot before = getQuota(command.userId(), command.quotaType(), command.quotaDate());
        if (!UserQuotaPolicy.canConsume(command.amount(), before.remaining())) {
            return new ConsumeQuotaResult(false, before);
        }

        int changed = switch (command.quotaType()) {
            case DAILY_API_CALL -> apiDailyUsageMapper.incrementUsageBy(
                    command.userId(), command.quotaDate(), command.amount());
            case STORAGE_BYTES -> updateStorage(command.userId(), command.amount());
        };
        if (changed <= 0) {
            return new ConsumeQuotaResult(false, getQuota(command.userId(), command.quotaType(), command.quotaDate()));
        }
        return new ConsumeQuotaResult(true, getQuota(command.userId(), command.quotaType(), command.quotaDate()));
    }

    @Override
    public void rollback(ConsumeQuotaCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        switch (command.quotaType()) {
            case DAILY_API_CALL -> apiDailyUsageMapper.decrementUsageBy(
                    command.userId(), command.quotaDate(), command.amount());
            case STORAGE_BYTES -> updateStorage(command.userId(), -command.amount());
        }
    }

    private QuotaSnapshot dailyApiQuota(long userId, LocalDate quotaDate) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(UserConstant.NOT_FOUND_USER);
        }
        ApiDailyUsageDO usage = apiDailyUsageMapper.selectByUserIdAndDate(userId, quotaDate);
        long used = usage == null ? 0L : UserQuotaPolicy.usedOrZero(usage.getUsedCount());
        return new QuotaSnapshot(userId, QuotaType.DAILY_API_CALL,
                RoleDataComputerUtil.getApiLimit(user.getRoleId()), used, quotaDate);
    }

    private QuotaSnapshot storageQuota(long userId) {
        UserQuoteStorageDTO storage = userMapper.selectQuoteStorageById(userId);
        if (storage == null) {
            throw new BaseException(UserConstant.NOT_FOUND_USER);
        }
        Long limit = storage.getMaxStorageBytes();
        if (limit == null) {
            limit = RoleDataComputerUtil.getStorage(storage.getRoleId());
            if (userMapper.updateMaxStorageById(userId, limit) <= 0) {
                throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
            }
        }
        return new QuotaSnapshot(userId, QuotaType.STORAGE_BYTES, limit,
                UserQuotaPolicy.usedOrZero(storage.getUsedStorageBytes()), null);
    }

    private int updateStorage(long userId, long deltaBytes) {
        UserStorageHandlerDTO update = new UserStorageHandlerDTO();
        update.setId(userId);
        update.setDeltaStorageBytes(deltaBytes);
        return userMapper.updateStorageById(update);
    }

    private static void validateQuery(long userId, QuotaType quotaType, LocalDate quotaDate) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(quotaType, "quotaType must not be null");
        if (UserQuotaPolicy.requiresQuotaDate(quotaType) && quotaDate == null) {
            throw new IllegalArgumentException("quotaDate is required for daily API quota");
        }
        if (!UserQuotaPolicy.requiresQuotaDate(quotaType) && quotaDate != null) {
            throw new IllegalArgumentException("quotaDate is not applicable to storage quota");
        }
    }
}
