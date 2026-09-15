package com.jacolp.module.system.biz.infrastructure.bootstrap;

import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.RoleDO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.RoleMapper;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.infrastructure.security.PasswordEncoder;
import com.jacolp.utils.RoleDataComputerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Value("${jacolp.admin.username}")
    private String adminUsername;

    @Value("${jacolp.admin.password}")
    private String adminPassword;

    @Value("${jacolp.admin.email}")
    private String adminEmail;

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;

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
        // 检查是否存在管理员账号
        UserDO creator = userMapper.selectById(1L);
        if (creator == null) {
            creator = userMapper.selectByUsername(adminUsername);
        }

        // 强制加入一个创建者角色
        creator = new UserDO();
        creator.setId(1L);
        creator.setUsername(adminUsername);
        creator.setPassword(passwordEncoder.encode(adminPassword));
        creator.setEmail(adminEmail);
        creator.setRoleId(RoleConstant.CREATOR);
        creator.setStatus(UserConstant.ACTIVE_STATUS);
        creator.setMaxStorageBytes(RoleDataComputerUtil.getStorage(RoleConstant.CREATOR));
        int count = userMapper.upsertCreator(creator);
        if (count <= 0) {
            log.error("Failed to create admin account!");
            throw new RuntimeException("Failed to create admin account!");
        }

        log.warn("The creator account init!");
    }
}
