package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;

public interface UserRoleMappingRepository extends JpaRepository<UserRoleMapping, UUID> {

	List<UserRoleMapping> findByUser_Id(String userId);

	boolean existsByUser_IdAndRole_Id(String userId, UUID roleId);
}
