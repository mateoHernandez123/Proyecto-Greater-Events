package ar.edu.unnoba.pdyc2026.notification.repository;

import ar.edu.unnoba.pdyc2026.notification.model.NotificationUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationUserRepository extends JpaRepository<NotificationUser, Long> {

    Optional<NotificationUser> findByKeycloakId(String keycloakId);
}
