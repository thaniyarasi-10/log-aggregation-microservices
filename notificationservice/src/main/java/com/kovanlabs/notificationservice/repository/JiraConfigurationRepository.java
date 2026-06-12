package com.kovanlabs.notificationservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kovanlabs.notificationservice.model.JiraConfiguration;

@Repository
public interface JiraConfigurationRepository extends JpaRepository<JiraConfiguration, UUID> {
    Optional<JiraConfiguration> findFirstByOrganizationIdAndActiveTrue(UUID organizationId);
    List<JiraConfiguration> findByOrganizationId(UUID organizationId);
    Optional<JiraConfiguration> findFirstByActiveTrue();
}
