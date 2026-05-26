package ar.edu.unnoba.pdyc2026.events.service;

import ar.edu.unnoba.pdyc2026.events.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.events.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.events.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.events.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.events.model.Artist;
import ar.edu.unnoba.pdyc2026.events.model.Event;
import ar.edu.unnoba.pdyc2026.events.model.EventState;
import ar.edu.unnoba.pdyc2026.events.model.User;
import ar.edu.unnoba.pdyc2026.events.repository.ArtistRepository;
import ar.edu.unnoba.pdyc2026.events.repository.EventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica de negocio asociada al usuario final autenticado (TP4).
 *
 * <p>Cubre los flujos de seguir/dejar de seguir artistas, favorito/no favorito
 * de eventos y los listados derivados que se consumen desde {@code /me/**}.
 */
@Service
public class EndUserService {

    private static final Set<EventState> ACTIVE_STATES =
            Set.of(EventState.CONFIRMED, EventState.RESCHEDULED);

    private final CurrentUserService currentUserService;
    private final ArtistRepository artistRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    public EndUserService(
            CurrentUserService currentUserService,
            ArtistRepository artistRepository,
            EventRepository eventRepository,
            Clock clock) {
        this.currentUserService = currentUserService;
        this.artistRepository = artistRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ArtistResponse> listFollowing() {
        User user = currentUserService.getOrProvisionWithFollowing();
        return user.getFollowingArtists().stream()
                .sorted(Comparator.comparing(Artist::getName))
                .map(EndUserService::toArtistResponse)
                .toList();
    }

    @Transactional
    public ArtistResponse followArtist(Long artistId) {
        User user = currentUserService.getOrProvisionWithFollowing();
        Artist artist = artistRepository
                .findById(artistId)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + artistId));
        if (!artist.isActive()) {
            throw new BusinessRuleException("Cannot follow an inactive artist.");
        }
        if (user.getFollowingArtists().stream().anyMatch(a -> a.getId().equals(artistId))) {
            throw new BusinessRuleException("Already following this artist.");
        }
        user.getFollowingArtists().add(artist);
        return toArtistResponse(artist);
    }

    @Transactional
    public void unfollowArtist(Long artistId) {
        User user = currentUserService.getOrProvisionWithFollowing();
        boolean removed = user.getFollowingArtists().removeIf(a -> a.getId().equals(artistId));
        if (!removed) {
            throw new ResourceNotFoundException("Artist " + artistId + " is not followed by the current user.");
        }
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listUpcomingEventsForFollowedArtists() {
        User user = currentUserService.getOrProvisionWithFollowing();
        List<Long> artistIds = user.getFollowingArtists().stream().map(Artist::getId).toList();
        if (artistIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return eventRepository.findUpcomingForArtistIds(artistIds, ACTIVE_STATES, now).stream()
                .map(EndUserService::toEventSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listFavoriteEvents() {
        User user = currentUserService.getOrProvisionWithFavorites();
        LocalDateTime now = LocalDateTime.now(clock);
        return user.getFavoriteEvents().stream()
                .filter(e -> ACTIVE_STATES.contains(e.getState()))
                .filter(e -> e.getStartDate().isAfter(now))
                .sorted(Comparator.comparing(Event::getStartDate))
                .map(EndUserService::toEventSummary)
                .toList();
    }

    @Transactional
    public EventSummaryResponse favoriteEvent(Long eventId) {
        User user = currentUserService.getOrProvisionWithFavorites();
        Event event = eventRepository
                .findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (event.getState() == EventState.TENTATIVE) {
            throw new BusinessRuleException("Tentative events cannot be marked as favorite.");
        }
        if (user.getFavoriteEvents().stream().anyMatch(e -> e.getId().equals(eventId))) {
            throw new BusinessRuleException("Event is already a favorite.");
        }
        user.getFavoriteEvents().add(event);
        return toEventSummary(event);
    }

    @Transactional
    public void removeFavoriteEvent(Long eventId) {
        User user = currentUserService.getOrProvisionWithFavorites();
        boolean removed = user.getFavoriteEvents().removeIf(e -> e.getId().equals(eventId));
        if (!removed) {
            throw new ResourceNotFoundException(
                    "Event " + eventId + " is not marked as favorite for the current user.");
        }
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
