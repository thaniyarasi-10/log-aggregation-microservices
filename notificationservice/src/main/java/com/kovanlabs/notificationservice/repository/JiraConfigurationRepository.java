package com.kovanlabs.notificationservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kovanlabs.notificationservice.model.JiraConfiguration;

@Repository
public interface JiraConfigurationRepository extends JpaRepository<JiraConfiguration, UUID> {
    Optional<JiraConfiguration> findFirstByActiveTrue();
}
