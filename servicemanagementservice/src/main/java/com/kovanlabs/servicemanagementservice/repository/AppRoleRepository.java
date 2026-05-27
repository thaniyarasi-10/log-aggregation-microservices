package com.kovanlabs.servicemanagementservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.AppRole;

public interface AppRoleRepository extends JpaRepository<AppRole, UUID> {

	Optional<AppRole> findByNameIgnoreCase(String name);
}
