package ar.edu.unnoba.pdyc2026.notification.repository;

import ar.edu.unnoba.pdyc2026.notification.model.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserKeycloakIdOrderByCreatedAtDesc(String userKeycloakId);

    List<Notification> findByUserKeycloakIdAndReadFalseOrderByCreatedAtDesc(String userKeycloakId);
}
