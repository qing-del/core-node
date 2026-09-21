package com.jacolp.system.infrastructure.persistence.repository;

import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.OAuth2RegisteredClientDO;
import com.jacolp.system.infrastructure.persistence.mapper.OAuth2RegisteredClientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-backed persistence adapter for OAuth2 registered-client metadata.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisOAuth2RegisteredClientMetadataRepository implements OAuth2RegisteredClientMetadataRepository {

    private final OAuth2RegisteredClientMapper registeredClientMapper;

    @Override
    public Optional<OAuth2RegisteredClientMetadata> findById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(registeredClientMapper.selectById(id))
                .map(MyBatisOAuth2RegisteredClientMetadataRepository::toMetadata);
    }

    @Override
    public Optional<OAuth2RegisteredClientMetadata> findByClientId(String clientId) {
        return clientId == null ? Optional.empty() : Optional.ofNullable(registeredClientMapper.selectByClientId(clientId))
                .map(MyBatisOAuth2RegisteredClientMetadataRepository::toMetadata);
    }

    @Override
    public List<OAuth2RegisteredClientMetadata> findByStatus(String status) {
        return status == null ? List.of() : registeredClientMapper.selectByStatus(status).stream()
                .map(MyBatisOAuth2RegisteredClientMetadataRepository::toMetadata)
                .toList();
    }

    @Override
    public int insert(OAuth2RegisteredClientMetadata registeredClient) {
        return registeredClientMapper.insert(toDataObject(registeredClient));
    }

    @Override
    public int updateById(OAuth2RegisteredClientMetadata registeredClient) {
        return registeredClientMapper.updateById(toDataObject(registeredClient));
    }

    @Override
    public int deleteById(String id) {
        return registeredClientMapper.deleteById(id);
    }

    private static OAuth2RegisteredClientMetadata toMetadata(OAuth2RegisteredClientDO registeredClient) {
        return new OAuth2RegisteredClientMetadata(registeredClient.getId(), registeredClient.getClientId(),
                registeredClient.getClientIdIssuedAt(), registeredClient.getClientSecret(),
                registeredClient.getClientSecretExpiresAt(), registeredClient.getClientName(),
                registeredClient.getClientAuthenticationMethods(), registeredClient.getAuthorizationGrantTypes(),
                registeredClient.getRedirectUris(), registeredClient.getPostLogoutRedirectUris(),
                registeredClient.getScopes(), registeredClient.getClientSettings(), registeredClient.getTokenSettings(),
                registeredClient.getAutoApprove(), registeredClient.getStatus(), registeredClient.getAllowedIps());
    }

    private static OAuth2RegisteredClientDO toDataObject(OAuth2RegisteredClientMetadata registeredClient) {
        OAuth2RegisteredClientDO dataObject = new OAuth2RegisteredClientDO();
        dataObject.setId(registeredClient.id());
        dataObject.setClientId(registeredClient.clientId());
        dataObject.setClientIdIssuedAt(registeredClient.clientIdIssuedAt());
        dataObject.setClientSecret(registeredClient.clientSecret());
        dataObject.setClientSecretExpiresAt(registeredClient.clientSecretExpiresAt());
        dataObject.setClientName(registeredClient.clientName());
        dataObject.setClientAuthenticationMethods(registeredClient.clientAuthenticationMethods());
        dataObject.setAuthorizationGrantTypes(registeredClient.authorizationGrantTypes());
        dataObject.setRedirectUris(registeredClient.redirectUris());
        dataObject.setPostLogoutRedirectUris(registeredClient.postLogoutRedirectUris());
        dataObject.setScopes(registeredClient.scopes());
        dataObject.setClientSettings(registeredClient.clientSettings());
        dataObject.setTokenSettings(registeredClient.tokenSettings());
        dataObject.setAutoApprove(registeredClient.autoApprove());
        dataObject.setStatus(registeredClient.status());
        dataObject.setAllowedIps(registeredClient.allowedIps());
        return dataObject;
    }
}
