package ar.edu.unnoba.pdyc2026.catalog.repository;

import ar.edu.unnoba.pdyc2026.catalog.model.Event;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import java.time.LocalDateTime;
import java.util.Collection;
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

    @Query("select distinct e from Event e left join fetch e.artists "
            + "where e.state in :states and e.startDate > :now order by e.startDate asc")
    List<Event> findPublicUpcoming(
            @Param("states") Collection<EventState> states, @Param("now") LocalDateTime now);

    @Query("select distinct e from Event e join e.artists a left join fetch e.artists "
            + "where a.id = :artistId and e.state in :states and e.startDate > :now order by e.startDate asc")
    List<Event> findUpcomingByArtist(
            @Param("artistId") Long artistId,
            @Param("states") Collection<EventState> states,
            @Param("now") LocalDateTime now);

    @Query("select distinct e from Event e join e.artists a left join fetch e.artists "
            + "where a.id in :artistIds and e.state in :states and e.startDate > :now order by e.startDate asc")
    List<Event> findUpcomingForArtistIds(
            @Param("artistIds") Collection<Long> artistIds,
            @Param("states") Collection<EventState> states,
            @Param("now") LocalDateTime now);
}
