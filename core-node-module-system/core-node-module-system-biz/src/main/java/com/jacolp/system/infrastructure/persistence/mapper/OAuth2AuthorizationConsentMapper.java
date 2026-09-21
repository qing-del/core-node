package com.jacolp.system.infrastructure.persistence.mapper;

import com.jacolp.system.infrastructure.persistence.dataobject.OAuth2AuthorizationConsentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OAuth2AuthorizationConsentMapper {

    OAuth2AuthorizationConsentDO selectByRegisteredClientIdAndPrincipalName(
            @Param("registeredClientId") String registeredClientId,
            @Param("principalName") String principalName);

    List<OAuth2AuthorizationConsentDO> selectByPrincipalName(@Param("principalName") String principalName);

    int insert(OAuth2AuthorizationConsentDO consent);

    int updateAuthorities(OAuth2AuthorizationConsentDO consent);

    int deleteByRegisteredClientIdAndPrincipalName(@Param("registeredClientId") String registeredClientId,
                                                    @Param("principalName") String principalName);
}
