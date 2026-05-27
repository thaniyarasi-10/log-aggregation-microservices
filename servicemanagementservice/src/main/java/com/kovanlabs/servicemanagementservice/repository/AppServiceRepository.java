package com.kovanlabs.servicemanagementservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.AppService;

public interface AppServiceRepository extends JpaRepository<AppService, UUID> {

    List<AppService> findByActiveTrueOrderByNameAsc();

    Optional<AppService> findByNameIgnoreCase(String name);
}
