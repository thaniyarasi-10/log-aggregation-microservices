package com.kovanlabs.notificationservice.repository;

import com.kovanlabs.notificationservice.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findAllByServiceIgnoreCaseAndTimestampAfter(String service, LocalDateTime timestamp);
}

