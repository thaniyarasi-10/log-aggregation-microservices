package com.kovanlabs.servicemanagementservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByDomainIgnoreCase(String domain);
}
