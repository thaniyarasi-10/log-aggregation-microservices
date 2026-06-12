package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.UserOrganizationMapping;

public interface UserOrganizationMappingRepository extends JpaRepository<UserOrganizationMapping, UUID> {
    List<UserOrganizationMapping> findByUser_Id(String userId);
    List<UserOrganizationMapping> findByUser_IdAndStatus(String userId, String status);
    Optional<UserOrganizationMapping> findByUser_IdAndOrganization_Id(String userId, UUID organizationId);
    Optional<UserOrganizationMapping> findByUser_IdAndOrganization_IdAndStatus(String userId, UUID organizationId, String status);
    List<UserOrganizationMapping> findByOrganization_IdAndStatus(UUID organizationId, String status);
    List<UserOrganizationMapping> findByOrganization_Id(UUID organizationId);
}
