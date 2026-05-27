package com.kovanlabs.notificationservice.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


import org.springframework.stereotype.Service;

import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.repository.NotificationPreferenceRepository;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    public NotificationPreference getOrCreatePreference(String email) {
        String normalized = normalize(email);
        return notificationPreferenceRepository.findByUserEmailIgnoreCase(normalized)
                .orElseGet(() -> notificationPreferenceRepository.save(createDefault(normalized)));
    }

    public NotificationPreference updatePreference(String email, boolean emailEnabled) {
        NotificationPreference preference = getOrCreatePreference(email);
        preference.setEmailEnabled(emailEnabled);
        preference.setUpdatedAt(LocalDateTime.now());
        return notificationPreferenceRepository.save(preference);
    }

    private NotificationPreference createDefault(String email) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserEmail(email);
        preference.setEmailEnabled(true);
        preference.setSmsEnabled(false);
        preference.setPushEnabled(false);
        preference.setCreatedAt(LocalDateTime.now());
        preference.setUpdatedAt(LocalDateTime.now());
        return preference;
    }

    private String normalize(String value) {
        return value == null ? "anonymous@local" : value.trim().toLowerCase();
    }
}