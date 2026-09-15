package com.jacolp.middleware.module.system.biz.infrastructure.persistence;

import com.jacolp.system.application.authorization.model.OAuth2AuthorizationConsentMetadata;
import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.port.out.OAuth2AuthorizationConsentMetadataRepository;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.OAuth2AuthorizationConsentDO;
import com.jacolp.system.infrastructure.persistence.dataobject.OAuth2RegisteredClientDO;
import com.jacolp.system.infrastructure.persistence.mapper.OAuth2AuthorizationConsentMapper;
import com.jacolp.system.infrastructure.persistence.mapper.OAuth2RegisteredClientMapper;
import com.jacolp.system.infrastructure.persistence.repository.MyBatisOAuth2AuthorizationConsentMetadataRepository;
import com.jacolp.system.infrastructure.persistence.repository.MyBatisOAuth2RegisteredClientMetadataRepository;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthMetadataPersistenceTest {

    @Test
    void mapperXmlDefinesAllOauthMetadataStatementsAndUsesTokenSettingsAsOneStoredValue() throws Exception {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/OAuth2RegisteredClientMapper.xml");
        parse(configuration, "mapper/OAuth2AuthorizationConsentMapper.xml");

        assertThat(configuration.hasStatement(
                "com.jacolp.system.infrastructure.persistence.mapper.OAuth2RegisteredClientMapper.selectByClientId"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.jacolp.system.infrastructure.persistence.mapper.OAuth2RegisteredClientMapper.updateById"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.jacolp.system.infrastructure.persistence.mapper.OAuth2AuthorizationConsentMapper.updateAuthorities"))
                .isTrue();

        assertThat(content("mapper/OAuth2RegisteredClientMapper.xml"))
                .contains("token_settings")
                .contains("token_settings = #{tokenSettings}")
                .contains("ORDER BY client_id ASC, id ASC")
                .doesNotContain("access_token_time_to_live")
                .doesNotContain("refresh_token_time_to_live");
        assertThat(content("mapper/OAuth2AuthorizationConsentMapper.xml"))
                .contains("ORDER BY registered_client_id ASC")
                .contains("authorities = #{authorities}");
    }

    @Test
    void applicationPortsExposeOnlyApplicationMetadataModels() {
        assertNoInfrastructurePersistenceType(OAuth2RegisteredClientMetadataRepository.class);
        assertNoInfrastructurePersistenceType(OAuth2AuthorizationConsentMetadataRepository.class);
    }

    @Test
    void registeredClientAdapterMapsEveryStoredMetadataFieldAndDelegatesCrud() {
        OAuth2RegisteredClientMapper mapper = mock(OAuth2RegisteredClientMapper.class);
        MyBatisOAuth2RegisteredClientMetadataRepository repository =
                new MyBatisOAuth2RegisteredClientMetadataRepository(mapper);
        OAuth2RegisteredClientDO dataObject = registeredClientDataObject();
        OAuth2RegisteredClientMetadata metadata = registeredClientMetadata();
        when(mapper.selectById("client-row-1")).thenReturn(dataObject);
        when(mapper.selectByClientId("user")).thenReturn(dataObject);
        when(mapper.selectByStatus("disabled")).thenReturn(List.of(dataObject));
        when(mapper.insert(any(OAuth2RegisteredClientDO.class))).thenReturn(1);
        when(mapper.updateById(any(OAuth2RegisteredClientDO.class))).thenReturn(1);
        when(mapper.deleteById("client-row-1")).thenReturn(1);

        Assertions.assertThat(repository.findById("client-row-1")).contains(metadata);
        Assertions.assertThat(repository.findByClientId("user")).contains(metadata);
        Assertions.assertThat(repository.findByStatus("disabled")).containsExactly(metadata);
        assertThat(repository.insert(metadata)).isEqualTo(1);
        assertThat(repository.updateById(metadata)).isEqualTo(1);
        assertThat(repository.deleteById("client-row-1")).isEqualTo(1);
        verify(mapper).selectById("client-row-1");
        verify(mapper).selectByClientId("user");
        verify(mapper).selectByStatus("disabled");
        verify(mapper).insert(dataObject);
        verify(mapper).updateById(dataObject);
        verify(mapper).deleteById("client-row-1");
    }

    @Test
    void authorizationConsentAdapterMapsCompositeKeyMetadataAndDelegatesCrud() {
        OAuth2AuthorizationConsentMapper mapper = mock(OAuth2AuthorizationConsentMapper.class);
        MyBatisOAuth2AuthorizationConsentMetadataRepository repository =
                new MyBatisOAuth2AuthorizationConsentMetadataRepository(mapper);
        OAuth2AuthorizationConsentDO dataObject = authorizationConsentDataObject();
        OAuth2AuthorizationConsentMetadata metadata = authorizationConsentMetadata();
        when(mapper.selectByRegisteredClientIdAndPrincipalName("client-row-1", "42")).thenReturn(dataObject);
        when(mapper.selectByPrincipalName("42")).thenReturn(List.of(dataObject));
        when(mapper.insert(any(OAuth2AuthorizationConsentDO.class))).thenReturn(1);
        when(mapper.updateAuthorities(any(OAuth2AuthorizationConsentDO.class))).thenReturn(1);
        when(mapper.deleteByRegisteredClientIdAndPrincipalName("client-row-1", "42")).thenReturn(1);

        Assertions.assertThat(repository.findByRegisteredClientIdAndPrincipalName("client-row-1", "42")).contains(metadata);
        Assertions.assertThat(repository.findByPrincipalName("42")).containsExactly(metadata);
        assertThat(repository.insert(metadata)).isEqualTo(1);
        assertThat(repository.updateAuthorities(metadata)).isEqualTo(1);
        assertThat(repository.deleteByRegisteredClientIdAndPrincipalName("client-row-1", "42")).isEqualTo(1);
        verify(mapper).selectByRegisteredClientIdAndPrincipalName("client-row-1", "42");
        verify(mapper).selectByPrincipalName("42");
        verify(mapper).insert(dataObject);
        verify(mapper).updateAuthorities(dataObject);
        verify(mapper).deleteByRegisteredClientIdAndPrincipalName("client-row-1", "42");
    }

    private static void parse(Configuration configuration, String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            new XMLMapperBuilder(inputStream, configuration, resource.getDescription(), configuration.getSqlFragments())
                    .parse();
        }
    }

    private static String content(String resourcePath) throws Exception {
        return new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
    }

    private static void assertNoInfrastructurePersistenceType(Class<?> portType) {
        for (Method method : portType.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain(".infrastructure.persistence.");
            for (java.lang.reflect.Type parameterType : method.getGenericParameterTypes()) {
                assertThat(parameterType.getTypeName()).doesNotContain(".infrastructure.persistence.");
            }
        }
    }

    private static OAuth2RegisteredClientDO registeredClientDataObject() {
        OAuth2RegisteredClientDO registeredClient = new OAuth2RegisteredClientDO();
        registeredClient.setId("client-row-1");
        registeredClient.setClientId("user");
        registeredClient.setClientIdIssuedAt(LocalDateTime.of(2026, 8, 10, 1, 2, 3));
        registeredClient.setClientSecret("{bcrypt}hash");
        registeredClient.setClientSecretExpiresAt(LocalDateTime.of(2027, 8, 10, 1, 2, 3));
        registeredClient.setClientName("CORE NODE User Client");
        registeredClient.setClientAuthenticationMethods("client_secret_post");
        registeredClient.setAuthorizationGrantTypes("password,user_password,refresh_token");
        registeredClient.setRedirectUris(null);
        registeredClient.setPostLogoutRedirectUris(null);
        registeredClient.setScopes("*:read,*:write");
        registeredClient.setClientSettings("{\"require-proof-key\":false}");
        registeredClient.setTokenSettings("{\"settings.token.access-token-time-to-live\":\"PT3H\"}");
        registeredClient.setAutoApprove("*:read,*:write");
        registeredClient.setStatus("disabled");
        registeredClient.setAllowedIps("0.0.0.0/0");
        return registeredClient;
    }

    private static OAuth2RegisteredClientMetadata registeredClientMetadata() {
        OAuth2RegisteredClientDO registeredClient = registeredClientDataObject();
        return new OAuth2RegisteredClientMetadata(registeredClient.getId(), registeredClient.getClientId(),
                registeredClient.getClientIdIssuedAt(), registeredClient.getClientSecret(),
                registeredClient.getClientSecretExpiresAt(), registeredClient.getClientName(),
                registeredClient.getClientAuthenticationMethods(), registeredClient.getAuthorizationGrantTypes(),
                registeredClient.getRedirectUris(), registeredClient.getPostLogoutRedirectUris(),
                registeredClient.getScopes(), registeredClient.getClientSettings(), registeredClient.getTokenSettings(),
                registeredClient.getAutoApprove(), registeredClient.getStatus(), registeredClient.getAllowedIps());
    }

    private static OAuth2AuthorizationConsentDO authorizationConsentDataObject() {
        OAuth2AuthorizationConsentDO consent = new OAuth2AuthorizationConsentDO();
        consent.setRegisteredClientId("client-row-1");
        consent.setPrincipalName("42");
        consent.setAuthorities("SCOPE_note:read");
        return consent;
    }

    private static OAuth2AuthorizationConsentMetadata authorizationConsentMetadata() {
        OAuth2AuthorizationConsentDO consent = authorizationConsentDataObject();
        return new OAuth2AuthorizationConsentMetadata(consent.getRegisteredClientId(), consent.getPrincipalName(),
                consent.getAuthorities());
    }
}
