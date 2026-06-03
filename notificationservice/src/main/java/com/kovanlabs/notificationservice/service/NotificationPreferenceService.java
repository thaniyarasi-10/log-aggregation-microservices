package com.kovanlabs.notificationservice.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.repository.NotificationPreferenceRepository;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    public NotificationPreference getOrCreatePreference(String userId) {
        String normalized = normalize(userId);
        return notificationPreferenceRepository.findByUserId(normalized)
                .orElseGet(() -> notificationPreferenceRepository.save(createDefault(normalized)));
    }

    public NotificationPreference updatePreference(String userId, boolean emailEnabled) {
        NotificationPreference preference = getOrCreatePreference(userId);
        preference.setEmailEnabled(emailEnabled);
        preference.setUpdatedAt(LocalDateTime.now());
        return notificationPreferenceRepository.save(preference);
    }

    private NotificationPreference createDefault(String userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setEmailEnabled(true);
        preference.setSmsEnabled(false);
        preference.setPushEnabled(false);
        preference.setCreatedAt(LocalDateTime.now());
        preference.setUpdatedAt(LocalDateTime.now());
        return preference;
    }

    private String normalize(String value) {
        return value == null ? "anonymous" : value.trim();
    }
}