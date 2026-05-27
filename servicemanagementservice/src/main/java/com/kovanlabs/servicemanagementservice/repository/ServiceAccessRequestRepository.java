package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.ServiceAccessRequest;

public interface ServiceAccessRequestRepository extends JpaRepository<ServiceAccessRequest, UUID> {

    List<ServiceAccessRequest> findByRequestedByOrderByCreatedAtDesc(String requestedBy);

    List<ServiceAccessRequest> findAllByOrderByCreatedAtDesc();

    List<ServiceAccessRequest> findByRequestedByAndServiceNameIgnoreCaseAndStatus(
            String requestedBy,
            String serviceName,
            ServiceAccessRequest.RequestStatus status);
}
