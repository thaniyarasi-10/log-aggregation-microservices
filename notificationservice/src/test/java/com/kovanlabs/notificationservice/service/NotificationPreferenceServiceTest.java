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
        when(repository.findByUserEmailIgnoreCase("dev@test.com")).thenReturn(Optional.empty());
        when(repository.save(any(NotificationPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference preference = service.getOrCreatePreference("dev@test.com");
        assertThat(preference.isEmailEnabled()).isTrue();
        assertThat(preference.getUserEmail()).isEqualTo("dev@test.com");
    }

    @Test
    void updatePreference_persistsLatestFlag() {
        NotificationPreference existing = new NotificationPreference();
        existing.setUserEmail("dev@test.com");
        existing.setEmailEnabled(true);

        when(repository.findByUserEmailIgnoreCase("dev@test.com")).thenReturn(Optional.of(existing));
        when(repository.save(any(NotificationPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreference updated = service.updatePreference("dev@test.com", false);
        assertThat(updated.isEmailEnabled()).isFalse();
    }
}