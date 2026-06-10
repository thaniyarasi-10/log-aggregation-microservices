package com.kovanlabs.notificationservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kovanlabs.notificationservice.model.UserJiraMapping;

public interface UserJiraMappingRepository extends JpaRepository<UserJiraMapping, UUID> {

    Optional<UserJiraMapping> findByUserId(String userId);

    @Query(value = "SELECT username FROM app_user WHERE id = :userId", nativeQuery = true)
    Optional<String> findUsernameByUserId(@Param("userId") String userId);

    @Query(value = "SELECT DISTINCT u.email FROM user_service_mapping usm " +
                   "JOIN app_user u ON usm.user_id = u.id " +
                   "JOIN app_service s ON usm.service_id = s.id " +
                   "WHERE LOWER(s.name) = LOWER(:serviceName) AND u.email IS NOT NULL AND u.email != ''",
           nativeQuery = true)
    List<String> findEmailsByServiceNameIgnoreCase(@Param("serviceName") String serviceName);

    @Query(value = "SELECT u.id, u.username, usm.is_primary FROM user_service_mapping usm " +
                   "JOIN app_user u ON usm.user_id = u.id " +
                   "JOIN app_service s ON usm.service_id = s.id " +
                   "WHERE LOWER(s.name) = LOWER(:serviceName) " +
                   "ORDER BY usm.is_primary DESC, usm.created_at ASC", 
           nativeQuery = true)
    List<Object[]> findOwnersByServiceNameIgnoreCase(@Param("serviceName") String serviceName);

    @Query(value = "SELECT u.id, u.username FROM user_service_mapping usm " +
                   "JOIN app_user u ON usm.user_id = u.id " +
                   "JOIN app_service s ON usm.service_id = s.id " +
                   "WHERE LOWER(s.name) = LOWER(:serviceName) AND usm.is_primary = true", 
           nativeQuery = true)
    List<Object[]> findPrimaryOwnersByServiceNameIgnoreCase(@Param("serviceName") String serviceName);

    @Query(value = "SELECT " +
                   "  u.id AS user_id, " +
                   "  u.username AS username, " +
                   "  COALESCE(string_agg(s.name, ', '), '') AS owned_services, " +
                   "  m.id AS mapping_id, " +
                   "  m.jira_account_id AS jira_account_id, " +
                   "  m.jira_display_name AS jira_display_name, " +
                   "  m.active AS active " +
                   "FROM app_user u " +
                   "JOIN user_service_mapping usm ON u.id = usm.user_id " +
                   "JOIN app_service s ON usm.service_id = s.id " +
                   "LEFT JOIN user_jira_mapping m ON u.id = m.user_id " +
                   "GROUP BY u.id, u.username, m.id, m.jira_account_id, m.jira_display_name, m.active " +
                   "ORDER BY u.username ASC", 
           nativeQuery = true)
    List<Object[]> findAllUserMappingsWithServices();
}
