package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.OrganizationInvite;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {
    List<OrganizationInvite> findByOrganization_Id(UUID organizationId);
    Optional<OrganizationInvite> findByOrganization_IdAndEmailIgnoreCaseAndStatus(UUID organizationId, String email, String status);
    List<OrganizationInvite> findByEmailIgnoreCaseAndStatus(String email, String status);
}
