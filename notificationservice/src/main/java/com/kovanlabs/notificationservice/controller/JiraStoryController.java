package com.kovanlabs.notificationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.notificationservice.dto.AlertRequest;
import com.kovanlabs.notificationservice.dto.JiraStoryResponse;
import com.kovanlabs.notificationservice.service.JiraStoryService;

@RestController
@RequestMapping("/api/notifications/jira/stories")
public class JiraStoryController {

    private final JiraStoryService jiraStoryService;

    public JiraStoryController(JiraStoryService jiraStoryService) {
        this.jiraStoryService = jiraStoryService;
    }

    @PostMapping
    public ResponseEntity<JiraStoryResponse> triggerJiraStory(@RequestBody AlertRequest request) {
        JiraStoryResponse response = jiraStoryService.triggerJiraStoryCreation(request);
        // Jira failures must never block alert creation / return gracefully.
        return ResponseEntity.ok(response);
    }
}
