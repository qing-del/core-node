package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * Bridges the application client-metadata port to Spring Authorization Server.
 *
 * <p>The metadata lookup is deliberately the authorization gate: only a client
 * whose persisted status is exactly {@code active} reaches SAS's JDBC parser.
 * The SAS delegate remains the sole reader of CSV and JSON settings values.</p>
 */
@Component
public class ActiveRegisteredClientRepository implements RegisteredClientRepository {

    private static final String ACTIVE_STATUS = "active";

    private final OAuth2RegisteredClientMetadataRepository metadataRepository;
    private final RegisteredClientRepository sasDelegate;

    @Autowired
    public ActiveRegisteredClientRepository(OAuth2RegisteredClientMetadataRepository metadataRepository,
                                            JdbcOperations jdbcOperations) {
        this(metadataRepository, sasRepository(jdbcOperations));
    }

    ActiveRegisteredClientRepository(OAuth2RegisteredClientMetadataRepository metadataRepository,
                                     RegisteredClientRepository sasDelegate) {
        Assert.notNull(metadataRepository, "metadataRepository cannot be null");
        Assert.notNull(sasDelegate, "sasDelegate cannot be null");
        this.metadataRepository = metadataRepository;
        this.sasDelegate = sasDelegate;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Registered-client management is not available in this release");
    }

    @Override
    public RegisteredClient findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        Optional<OAuth2RegisteredClientMetadata> metadata = metadataRepository.findById(id);
        if (metadata.filter(ActiveRegisteredClientRepository::isActive).isEmpty()) {
            return null;
        }
        RegisteredClient registeredClient = sasDelegate.findById(id);
        return requireConsistent(registeredClient, metadata.get(), id, null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        Assert.hasText(clientId, "clientId cannot be empty");
        Optional<OAuth2RegisteredClientMetadata> metadata = metadataRepository.findByClientId(clientId);
        if (metadata.filter(ActiveRegisteredClientRepository::isActive).isEmpty()) {
            return null;
        }
        RegisteredClient registeredClient = sasDelegate.findByClientId(clientId);
        return requireConsistent(registeredClient, metadata.get(), null, clientId);
    }

    private static RegisteredClientRepository sasRepository(JdbcOperations jdbcOperations) {
        Assert.notNull(jdbcOperations, "jdbcOperations cannot be null");
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcOperations);
        repository.setRegisteredClientRowMapper(
                new JdbcRegisteredClientRepository.JsonMapperRegisteredClientRowMapper());
        return repository;
    }

    private static boolean isActive(OAuth2RegisteredClientMetadata metadata) {
        return ACTIVE_STATUS.equals(metadata.status());
    }

    private static RegisteredClient requireConsistent(RegisteredClient registeredClient,
                                                      OAuth2RegisteredClientMetadata metadata,
                                                      String expectedId,
                                                      String expectedClientId) {
        if (registeredClient == null
                || !metadata.id().equals(registeredClient.getId())
                || !metadata.clientId().equals(registeredClient.getClientId())
                || (expectedId != null && !expectedId.equals(registeredClient.getId()))
                || (expectedClientId != null && !expectedClientId.equals(registeredClient.getClientId()))) {
            throw new IllegalStateException("Registered-client metadata and SAS client record are inconsistent");
        }
        return registeredClient;
    }
}
