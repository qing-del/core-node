package com.jacolp.middleware.module.system.biz.infrastructure.persistence;

import com.jacolp.system.infrastructure.persistence.dataobject.RoleDO;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.RoleMapper;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationFoundationMapperContractTest {

    @Test
    void userExtraGrantTypesAreMappedSafelyAcrossExistingReadAndWritePaths() throws Exception {
        assertThat(field(UserDO.class, "extraGrantTypes").getType()).isEqualTo(String.class);
        assertThat(Arrays.stream(UserDO.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("grantTypes");

        Method byRoleId = UserMapper.class.getMethod("selectByRoleId", Integer.class);
        assertThat(byRoleId.getAnnotation(Select.class).value())
                .containsExactly("select id, username, nickname, email, role_id, extra_grant_types, status from sys_user where role_id = #{roleId}");

        String mapperXml = new ClassPathResource("mapper/UserMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(mapperXml)
                .contains("INSERT INTO sys_user (id, username, password, email, role_id, extra_grant_types, status")
                .contains("<if test=\"extraGrantTypes != null\">")
                .contains("u.extra_grant_types")
                .contains("role_id, extra_grant_types, status")
                .contains("INSERT INTO sys_user (username, password, email, role_id, extra_grant_types")
                .contains("insert into sys_user (id, username, nickname, email, role_id, extra_grant_types")
                .contains("extra_grant_types = values(extra_grant_types)")
                .doesNotContain("grantTypes")
                .doesNotContain("extraGrantTypes != null and extraGrantTypes != ''");
        assertThat(Pattern.compile("(?<!extra_)grant_types").matcher(mapperXml).find()).isFalse();
    }

    @Test
    void roleRankUsesTheExistingUnderscoreToCamelCaseSelectStarMapping() throws Exception {
        assertThat(field(RoleDO.class, "rank").getType()).isEqualTo(Integer.class);
        assertThat(RoleMapper.class.getMethod("getById", long.class).getAnnotation(Select.class).value())
                .containsExactly("select * from sys_role where id = #{id}");
        assertThat(RoleMapper.class.getMethod("getAll").getAnnotation(Select.class).value())
                .containsExactly("select * from sys_role");
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }
}
