package ar.edu.unnoba.pdyc2026.usersocial.repository;

import ar.edu.unnoba.pdyc2026.usersocial.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakId(String keycloakId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
