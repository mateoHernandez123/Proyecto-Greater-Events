package ar.edu.unnoba.pdyc2026.usersocial.repository;

import ar.edu.unnoba.pdyc2026.usersocial.model.UserFollowing;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFollowingRepository extends JpaRepository<UserFollowing, Long> {

    List<UserFollowing> findByUserIdOrderByArtistIdAsc(Long userId);

    boolean existsByUserIdAndArtistId(Long userId, Long artistId);

    void deleteByUserIdAndArtistId(Long userId, Long artistId);

    List<UserFollowing> findDistinctByArtistIdIn(Collection<Long> artistIds);
}
