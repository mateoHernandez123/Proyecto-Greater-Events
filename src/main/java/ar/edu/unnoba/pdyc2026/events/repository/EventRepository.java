package ar.edu.unnoba.pdyc2026.events.repository;

import ar.edu.unnoba.pdyc2026.events.model.Event;
import ar.edu.unnoba.pdyc2026.events.model.EventState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByArtists_Id(Long artistId);

    @Query("select distinct e from Event e left join fetch e.artists where (:state is null or e.state = :state)")
    List<Event> findAllForList(@Param("state") EventState state);

    @EntityGraph(attributePaths = "artists")
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findWithArtistsById(@Param("id") Long id);
}
