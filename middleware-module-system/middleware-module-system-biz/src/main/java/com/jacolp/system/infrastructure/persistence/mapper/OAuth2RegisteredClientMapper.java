package com.jacolp.system.infrastructure.persistence.mapper;

import com.jacolp.system.infrastructure.persistence.dataobject.OAuth2RegisteredClientDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OAuth2RegisteredClientMapper {

    OAuth2RegisteredClientDO selectById(@Param("id") String id);

    OAuth2RegisteredClientDO selectByClientId(@Param("clientId") String clientId);

    List<OAuth2RegisteredClientDO> selectByStatus(@Param("status") String status);

    int insert(OAuth2RegisteredClientDO registeredClient);

    int updateById(OAuth2RegisteredClientDO registeredClient);

    int deleteById(@Param("id") String id);
}
