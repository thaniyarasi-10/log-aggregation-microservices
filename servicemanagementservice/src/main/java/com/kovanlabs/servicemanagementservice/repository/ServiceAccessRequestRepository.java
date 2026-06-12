package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.ServiceAccessRequest;

public interface ServiceAccessRequestRepository extends JpaRepository<ServiceAccessRequest, UUID> {

    List<ServiceAccessRequest> findByOrganizationIdAndRequestedByOrderByCreatedAtDesc(UUID organizationId, String requestedBy);

    List<ServiceAccessRequest> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<ServiceAccessRequest> findByOrganizationIdAndRequestedByAndServiceNameIgnoreCaseAndStatus(
            UUID organizationId,
            String requestedBy,
            String serviceName,
            ServiceAccessRequest.RequestStatus status);

    boolean existsByOrganizationIdAndServiceNameIgnoreCase(UUID organizationId, String serviceName);

    boolean existsByOrganizationIdAndServiceNameIgnoreCaseAndStatus(UUID organizationId, String serviceName, ServiceAccessRequest.RequestStatus status);

    List<ServiceAccessRequest> findByRequestedByOrderByCreatedAtDesc(String requestedBy);

    List<ServiceAccessRequest> findAllByOrderByCreatedAtDesc();

    List<ServiceAccessRequest> findByRequestedByAndServiceNameIgnoreCaseAndStatus(
            String requestedBy,
            String serviceName,
            ServiceAccessRequest.RequestStatus status);

    boolean existsByServiceNameIgnoreCase(String serviceName);

    boolean existsByServiceNameIgnoreCaseAndStatus(String serviceName, ServiceAccessRequest.RequestStatus status);
}
