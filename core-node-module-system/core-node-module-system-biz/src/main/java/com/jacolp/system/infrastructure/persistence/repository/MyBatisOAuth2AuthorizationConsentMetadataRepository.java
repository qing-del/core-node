package com.jacolp.system.infrastructure.persistence.repository;

import com.jacolp.system.application.authorization.model.OAuth2AuthorizationConsentMetadata;
import com.jacolp.system.application.port.out.OAuth2AuthorizationConsentMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.OAuth2AuthorizationConsentDO;
import com.jacolp.system.infrastructure.persistence.mapper.OAuth2AuthorizationConsentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-backed persistence adapter for OAuth2 authorization-consent metadata.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisOAuth2AuthorizationConsentMetadataRepository implements OAuth2AuthorizationConsentMetadataRepository {

    private final OAuth2AuthorizationConsentMapper authorizationConsentMapper;

    @Override
    public Optional<OAuth2AuthorizationConsentMetadata> findByRegisteredClientIdAndPrincipalName(
            String registeredClientId, String principalName) {
        if (registeredClientId == null || principalName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(authorizationConsentMapper.selectByRegisteredClientIdAndPrincipalName(
                registeredClientId, principalName)).map(MyBatisOAuth2AuthorizationConsentMetadataRepository::toMetadata);
    }

    @Override
    public List<OAuth2AuthorizationConsentMetadata> findByPrincipalName(String principalName) {
        return principalName == null ? List.of() : authorizationConsentMapper.selectByPrincipalName(principalName).stream()
                .map(MyBatisOAuth2AuthorizationConsentMetadataRepository::toMetadata)
                .toList();
    }

    @Override
    public int insert(OAuth2AuthorizationConsentMetadata consent) {
        return authorizationConsentMapper.insert(toDataObject(consent));
    }

    @Override
    public int updateAuthorities(OAuth2AuthorizationConsentMetadata consent) {
        return authorizationConsentMapper.updateAuthorities(toDataObject(consent));
    }

    @Override
    public int deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName) {
        return authorizationConsentMapper.deleteByRegisteredClientIdAndPrincipalName(registeredClientId, principalName);
    }

    private static OAuth2AuthorizationConsentMetadata toMetadata(OAuth2AuthorizationConsentDO consent) {
        return new OAuth2AuthorizationConsentMetadata(consent.getRegisteredClientId(), consent.getPrincipalName(),
                consent.getAuthorities());
    }

    private static OAuth2AuthorizationConsentDO toDataObject(OAuth2AuthorizationConsentMetadata consent) {
        OAuth2AuthorizationConsentDO dataObject = new OAuth2AuthorizationConsentDO();
        dataObject.setRegisteredClientId(consent.registeredClientId());
        dataObject.setPrincipalName(consent.principalName());
        dataObject.setAuthorities(consent.authorities());
        return dataObject;
    }
}
