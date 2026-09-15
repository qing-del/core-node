package com.jacolp.system.infrastructure.bootstrap;

import com.jacolp.system.infrastructure.persistence.dataobject.RoleDO;
import com.jacolp.system.infrastructure.persistence.mapper.RoleMapper;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.system.application.authorization.CreatorAccountSynchronizationService;
import com.jacolp.system.utils.RoleDataComputerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
/**
 * 数据初始化
 * 强制 创建/修改 出一个 Creator 角色的账号
 * 固定改动 id 为 1 的账号
 */
public class DataInitializer implements CommandLineRunner {
    private final RoleMapper roleMapper;
    private final CreatorAccountSynchronizationService creatorAccountSynchronizationService;

    public DataInitializer(RoleMapper roleMapper,
                           CreatorAccountSynchronizationService creatorAccountSynchronizationService) {
        this.roleMapper = roleMapper;
        this.creatorAccountSynchronizationService = creatorAccountSynchronizationService;
    }

    @Value("${jacolp.admin.username}")
    private String adminUsername;

    @Value("${jacolp.admin.password}")
    private String adminPassword;

    @Value("${jacolp.admin.email}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        initRoleData();
        initCreatorAccount();
    }

    private void initRoleData() {
        List<RoleDO> roles = roleMapper.getAll();

        for (RoleDO role : roles) {
            RoleDataComputerUtil.putStorage(role.getId(), role.getMaxStorageBytes());
            RoleDataComputerUtil.putApiLimit(role.getId(), role.getDailyApiLimit());
        }

        log.info("Init role data success!");
    }

    private void initCreatorAccount() {
        creatorAccountSynchronizationService.synchronize(adminUsername, adminPassword, adminEmail,
                RoleDataComputerUtil.getStorage(RoleConstant.CREATOR));
        log.warn("The creator account init!");
    }
}
