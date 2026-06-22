package ar.edu.unnoba.pdyc2026.usersocial.repository;

import ar.edu.unnoba.pdyc2026.usersocial.model.UserFavoriteEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteEventRepository extends JpaRepository<UserFavoriteEvent, Long> {

    List<UserFavoriteEvent> findByUserIdOrderByEventIdAsc(Long userId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    void deleteByUserIdAndEventId(Long userId, Long eventId);

    List<UserFavoriteEvent> findByEventId(Long eventId);
}
