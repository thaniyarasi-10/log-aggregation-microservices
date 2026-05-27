package com.kovanlabs.servicemanagementservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.RolePermissionMapping;

public interface RolePermissionMappingRepository extends JpaRepository<RolePermissionMapping, UUID> {

	List<RolePermissionMapping> findByRole_IdIn(Collection<UUID> roleIds);

	boolean existsByRole_IdAndPermission_Id(UUID roleId, UUID permissionId);
}
