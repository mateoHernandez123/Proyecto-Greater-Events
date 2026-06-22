package ar.edu.unnoba.pdyc2026.usersocial.service;

import ar.edu.unnoba.pdyc2026.common.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.common.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.common.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import ar.edu.unnoba.pdyc2026.usersocial.client.CatalogClient;
import ar.edu.unnoba.pdyc2026.usersocial.model.User;
import ar.edu.unnoba.pdyc2026.usersocial.model.UserFavoriteEvent;
import ar.edu.unnoba.pdyc2026.usersocial.model.UserFollowing;
import ar.edu.unnoba.pdyc2026.usersocial.repository.UserFavoriteEventRepository;
import ar.edu.unnoba.pdyc2026.usersocial.repository.UserFollowingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndUserService {

    private static final Set<EventState> ACTIVE_STATES =
            Set.of(EventState.CONFIRMED, EventState.RESCHEDULED);

    private final CurrentUserService currentUserService;
    private final UserFollowingRepository userFollowingRepository;
    private final UserFavoriteEventRepository userFavoriteEventRepository;
    private final CatalogClient catalogClient;
    private final Clock clock;

    public EndUserService(
            CurrentUserService currentUserService,
            UserFollowingRepository userFollowingRepository,
            UserFavoriteEventRepository userFavoriteEventRepository,
            CatalogClient catalogClient,
            Clock clock) {
        this.currentUserService = currentUserService;
        this.userFollowingRepository = userFollowingRepository;
        this.userFavoriteEventRepository = userFavoriteEventRepository;
        this.catalogClient = catalogClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ArtistResponse> listFollowing() {
        User user = currentUserService.getOrProvisionCurrentUser();
        return userFollowingRepository.findByUserIdOrderByArtistIdAsc(user.getId()).stream()
                .map(UserFollowing::getArtistId)
                .map(catalogClient::getArtist)
                .sorted(Comparator.comparing(ArtistResponse::name))
                .toList();
    }

    @Transactional
    public ArtistResponse followArtist(Long artistId) {
        User user = currentUserService.getOrProvisionCurrentUser();
        ArtistResponse artist;
        try {
            artist = catalogClient.getArtist(artistId);
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException("Artist not found: " + artistId);
        }
        if (!artist.active()) {
            throw new BusinessRuleException("Cannot follow an inactive artist.");
        }
        if (userFollowingRepository.existsByUserIdAndArtistId(user.getId(), artistId)) {
            throw new BusinessRuleException("Already following this artist.");
        }
        userFollowingRepository.save(new UserFollowing(user.getId(), artistId));
        return artist;
    }

    @Transactional
    public void unfollowArtist(Long artistId) {
        User user = currentUserService.getOrProvisionCurrentUser();
        if (!userFollowingRepository.existsByUserIdAndArtistId(user.getId(), artistId)) {
            throw new ResourceNotFoundException("Artist " + artistId + " is not followed by the current user.");
        }
        userFollowingRepository.deleteByUserIdAndArtistId(user.getId(), artistId);
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listUpcomingEventsForFollowedArtists() {
        User user = currentUserService.getOrProvisionCurrentUser();
        List<Long> artistIds = userFollowingRepository.findByUserIdOrderByArtistIdAsc(user.getId()).stream()
                .map(UserFollowing::getArtistId)
                .toList();
        if (artistIds.isEmpty()) {
            return List.of();
        }
        String joinedIds = artistIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return catalogClient.getUpcomingEvents(joinedIds);
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listFavoriteEvents() {
        User user = currentUserService.getOrProvisionCurrentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        return userFavoriteEventRepository.findByUserIdOrderByEventIdAsc(user.getId()).stream()
                .map(UserFavoriteEvent::getEventId)
                .map(catalogClient::getEvent)
                .filter(event -> ACTIVE_STATES.contains(event.state()))
                .filter(event -> event.startDate().isAfter(now))
                .sorted(Comparator.comparing(EventSummaryResponse::startDate))
                .toList();
    }

    @Transactional
    public EventSummaryResponse favoriteEvent(Long eventId) {
        User user = currentUserService.getOrProvisionCurrentUser();
        EventSummaryResponse event;
        try {
            event = catalogClient.getEvent(eventId);
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        if (event.state() == EventState.TENTATIVE) {
            throw new BusinessRuleException("Tentative events cannot be marked as favorite.");
        }
        if (userFavoriteEventRepository.existsByUserIdAndEventId(user.getId(), eventId)) {
            throw new BusinessRuleException("Event is already a favorite.");
        }
        userFavoriteEventRepository.save(new UserFavoriteEvent(user.getId(), eventId));
        return event;
    }

    @Transactional
    public void removeFavoriteEvent(Long eventId) {
        User user = currentUserService.getOrProvisionCurrentUser();
        if (!userFavoriteEventRepository.existsByUserIdAndEventId(user.getId(), eventId)) {
            throw new ResourceNotFoundException(
                    "Event " + eventId + " is not marked as favorite for the current user.");
        }
        userFavoriteEventRepository.deleteByUserIdAndEventId(user.getId(), eventId);
    }
}
