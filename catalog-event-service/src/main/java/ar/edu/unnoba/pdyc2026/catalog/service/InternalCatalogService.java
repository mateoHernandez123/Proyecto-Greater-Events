package ar.edu.unnoba.pdyc2026.catalog.service;

import ar.edu.unnoba.pdyc2026.catalog.model.Event;
import ar.edu.unnoba.pdyc2026.catalog.repository.EventRepository;
import ar.edu.unnoba.pdyc2026.common.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.common.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalCatalogService {

    private static final Set<EventState> ACTIVE_STATES =
            Set.of(EventState.CONFIRMED, EventState.RESCHEDULED);

    private final ArtistService artistService;
    private final EventRepository eventRepository;
    private final Clock clock;

    public InternalCatalogService(
            ArtistService artistService, EventRepository eventRepository, Clock clock) {
        this.artistService = artistService;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ArtistResponse getArtist(Long id) {
        return artistService.getArtist(id);
    }

    @Transactional(readOnly = true)
    public EventSummaryResponse getEvent(Long id) {
        Event event = eventRepository
                .findWithArtistsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
        return toEventSummary(event);
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> getUpcomingForArtistIds(String artistIdsParam) {
        if (artistIdsParam == null || artistIdsParam.isBlank()) {
            return List.of();
        }
        List<Long> artistIds = Arrays.stream(artistIdsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        if (artistIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return eventRepository.findUpcomingForArtistIds(artistIds, ACTIVE_STATES, now).stream()
                .map(InternalCatalogService::toEventSummary)
                .toList();
    }

    private static EventSummaryResponse toEventSummary(Event event) {
        return new EventSummaryResponse(
                event.getId(),
                event.getName(),
                event.getStartDate(),
                event.getState(),
                event.getArtists().size());
    }
}
