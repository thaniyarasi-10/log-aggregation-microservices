package com.kovanlabs.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository repository;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository);
    }

    @Test
    void getOrCreatePreference_defaultsEmailEnabledToTrue() {
        when(repository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(repository.save(any(NotificationPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference preference = service.getOrCreatePreference("user-123");
        assertThat(preference.isEmailEnabled()).isTrue();
        assertThat(preference.getUserId()).isEqualTo("user-123");
    }

    @Test
    void updatePreference_persistsLatestFlag() {
        NotificationPreference existing = new NotificationPreference();
        existing.setUserId("user-123");
        existing.setEmailEnabled(true);

        when(repository.findByUserId("user-123")).thenReturn(Optional.of(existing));
        when(repository.save(any(NotificationPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference updated = service.updatePreference("user-123", false);
        assertThat(updated.isEmailEnabled()).isFalse();
    }
}