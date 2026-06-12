package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.AppService;

public interface AppServiceRepository extends JpaRepository<AppService, UUID> {

    List<AppService> findByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);

    Optional<AppService> findByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    Optional<AppService> findByNameIgnoreCase(String name);

    Optional<AppService> findByServiceSecret(String serviceSecret);
}
