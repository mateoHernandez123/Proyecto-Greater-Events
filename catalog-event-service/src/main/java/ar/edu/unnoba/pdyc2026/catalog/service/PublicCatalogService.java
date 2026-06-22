package ar.edu.unnoba.pdyc2026.catalog.service;

import ar.edu.unnoba.pdyc2026.catalog.dto.EventDetailResponse;
import ar.edu.unnoba.pdyc2026.catalog.model.Artist;
import ar.edu.unnoba.pdyc2026.catalog.model.Event;
import ar.edu.unnoba.pdyc2026.catalog.repository.ArtistRepository;
import ar.edu.unnoba.pdyc2026.catalog.repository.EventRepository;
import ar.edu.unnoba.pdyc2026.common.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.common.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCatalogService {

    private static final Set<EventState> PUBLIC_STATES =
            Set.of(EventState.CONFIRMED, EventState.RESCHEDULED);

    private final ArtistRepository artistRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    public PublicCatalogService(
            ArtistRepository artistRepository, EventRepository eventRepository, Clock clock) {
        this.artistRepository = artistRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ArtistResponse> listActiveArtists() {
        return artistRepository.findAllByActiveTrue().stream()
                .sorted(Comparator.comparing(Artist::getName))
                .map(PublicCatalogService::toArtistResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listUpcomingEventsForArtist(Long artistId) {
        Artist artist = artistRepository
                .findById(artistId)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + artistId));
        if (!artist.isActive()) {
            throw new ResourceNotFoundException("Artist not found: " + artistId);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return eventRepository.findUpcomingByArtist(artistId, PUBLIC_STATES, now).stream()
                .map(PublicCatalogService::toEventSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listUpcomingEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        return eventRepository.findPublicUpcoming(PUBLIC_STATES, now).stream()
                .map(PublicCatalogService::toEventSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(Long id) {
        Event event = eventRepository
                .findWithArtistsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
        if (event.getState() == EventState.TENTATIVE) {
            throw new ResourceNotFoundException("Event not found: " + id);
        }
        List<ArtistResponse> artists = event.getArtists().stream()
                .sorted(Comparator.comparing(Artist::getName))
                .map(PublicCatalogService::toArtistResponse)
                .toList();
        return new EventDetailResponse(
                event.getId(),
                event.getName(),
                event.getStartDate(),
                event.getDescription(),
                event.getState(),
                artists);
    }

    private static ArtistResponse toArtistResponse(Artist artist) {
        return new ArtistResponse(artist.getId(), artist.getName(), artist.getGenre(), artist.isActive());
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
