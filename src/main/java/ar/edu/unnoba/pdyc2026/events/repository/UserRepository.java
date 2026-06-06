package ar.edu.unnoba.pdyc2026.events.repository;

import ar.edu.unnoba.pdyc2026.events.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakId(String keycloakId);

    @EntityGraph(attributePaths = {"followingArtists"})
    Optional<User> findWithFollowingArtistsByKeycloakId(String keycloakId);

    @EntityGraph(attributePaths = {"favoriteEvents"})
    Optional<User> findWithFavoriteEventsByKeycloakId(String keycloakId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Usuarios que tienen al evento {@code eventId} marcado como favorito.
     * Lo usa {@code NotificationService} para localizar destinatarios sin cargar
     * a todos los usuarios.
     */
    @Query("select u from User u join u.favoriteEvents e where e.id = :eventId")
    List<User> findByFavoriteEventId(@Param("eventId") Long eventId);

    /**
     * Usuarios que siguen al menos a uno de los artistas indicados.
     * {@code distinct} evita devolver el mismo usuario varias veces cuando
     * sigue a varios artistas del lineup.
     */
    @Query("select distinct u from User u join u.followingArtists a where a.id in :artistIds")
    List<User> findDistinctByFollowingArtistIdIn(@Param("artistIds") Collection<Long> artistIds);
}
