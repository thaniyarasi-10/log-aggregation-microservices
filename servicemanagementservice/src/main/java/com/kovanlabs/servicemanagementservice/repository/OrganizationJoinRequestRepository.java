package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.OrganizationJoinRequest;

public interface OrganizationJoinRequestRepository extends JpaRepository<OrganizationJoinRequest, UUID> {
    List<OrganizationJoinRequest> findByOrganization_Id(UUID organizationId);
    List<OrganizationJoinRequest> findByOrganization_IdAndStatus(UUID organizationId, String status);
    Optional<OrganizationJoinRequest> findByOrganization_IdAndUser_IdAndStatus(UUID organizationId, String userId, String status);
}
