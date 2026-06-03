package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.UserServiceMapping;

public interface UserServiceMappingRepository extends JpaRepository<UserServiceMapping, UUID> {

	List<UserServiceMapping> findByUser_Id(String userId);

	boolean existsByUser_IdAndService_Id(String userId, UUID serviceId);

	List<UserServiceMapping> findByService_Id(UUID serviceId);
}
