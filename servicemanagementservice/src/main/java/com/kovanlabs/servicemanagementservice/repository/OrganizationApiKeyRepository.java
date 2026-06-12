package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.OrganizationApiKey;

public interface OrganizationApiKeyRepository extends JpaRepository<OrganizationApiKey, UUID> {
    List<OrganizationApiKey> findByOrganization_Id(UUID organizationId);
    Optional<OrganizationApiKey> findByApiKeyHash(String apiKeyHash);
}
