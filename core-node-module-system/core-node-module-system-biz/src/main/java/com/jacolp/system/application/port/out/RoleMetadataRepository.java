package com.jacolp.system.application.port.out;

import com.jacolp.system.application.authorization.model.RoleMetadata;

import java.util.List;
import java.util.Optional;

/**
 * Read-only persistence port for the role catalogue.
 */
public interface RoleMetadataRepository {

    Optional<RoleMetadata> findById(Long id);

    List<RoleMetadata> findAll();
}
