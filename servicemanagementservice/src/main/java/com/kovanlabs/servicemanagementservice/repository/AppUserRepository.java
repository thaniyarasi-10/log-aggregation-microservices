package com.kovanlabs.servicemanagementservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kovanlabs.servicemanagementservice.model.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

	Optional<AppUser> findByEmailIgnoreCase(String email);

	Optional<AppUser> findByEmailIgnoreCaseAndActiveTrue(String email);
}
