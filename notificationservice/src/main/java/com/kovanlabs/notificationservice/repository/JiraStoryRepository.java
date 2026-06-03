package com.kovanlabs.notificationservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kovanlabs.notificationservice.model.JiraStory;

@Repository
public interface JiraStoryRepository extends JpaRepository<JiraStory, UUID> {
    List<JiraStory> findByAlertId(String alertId);
    boolean existsByAlertIdAndStatusIgnoreCase(String alertId, String status);
}
