package com.kovanlabs.servicemanagementservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.AppPermission;

public interface AppPermissionRepository extends JpaRepository<AppPermission, UUID> {

	Optional<AppPermission> findByNameIgnoreCase(String name);
}
