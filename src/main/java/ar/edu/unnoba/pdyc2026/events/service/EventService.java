package ar.edu.unnoba.pdyc2026.events.service;

import ar.edu.unnoba.pdyc2026.events.dto.AddArtistToEventRequest;
import ar.edu.unnoba.pdyc2026.events.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.events.dto.EventCreateRequest;
import ar.edu.unnoba.pdyc2026.events.dto.EventDetailResponse;
import ar.edu.unnoba.pdyc2026.events.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.events.dto.EventUpdateRequest;
import ar.edu.unnoba.pdyc2026.events.dto.RescheduleEventRequest;
import ar.edu.unnoba.pdyc2026.events.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.events.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.events.model.Artist;
import ar.edu.unnoba.pdyc2026.events.model.Event;
import ar.edu.unnoba.pdyc2026.events.model.EventState;
import ar.edu.unnoba.pdyc2026.events.repository.ArtistRepository;
import ar.edu.unnoba.pdyc2026.events.repository.EventRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ArtistRepository artistRepository;
    private final Clock clock;

    public EventService(EventRepository eventRepository, ArtistRepository artistRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.artistRepository = artistRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listEvents(Optional<EventState> state) {
        return eventRepository.findAllForList(state.orElse(null)).stream()
                .map(
                        e ->
                                new EventSummaryResponse(
                                        e.getId(),
                                        e.getName(),
                                        e.getStartDate(),
                                        e.getState(),
                                        e.getArtists().size()))
                .sorted(Comparator.comparing(EventSummaryResponse::startDate))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(Long id) {
        Event event =
                eventRepository
                        .findWithArtistsById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
        List<ArtistResponse> artists =
                event.getArtists().stream()
                        .sorted(Comparator.comparing(Artist::getName))
                        .map(a -> new ArtistResponse(a.getId(), a.getName(), a.getGenre(), a.isActive()))
                        .toList();
        return new EventDetailResponse(
                event.getId(),
                event.getName(),
                event.getStartDate(),
                event.getDescription(),
                event.getState(),
                artists);
    }

    @Transactional
    public EventDetailResponse createEvent(EventCreateRequest request) {
        Event event = new Event();
        event.setName(request.name().trim());
        event.setDescription(request.description().trim());
        event.setStartDate(request.startDate());
        event.setState(EventState.TENTATIVE);
        Event saved = eventRepository.save(event);
        return getEvent(saved.getId());
    }

    @Transactional
    public EventDetailResponse updateEventDetails(Long id, EventUpdateRequest request) {
        Event event = loadEvent(id);
        requireState(event, EventState.TENTATIVE, "Only tentative events can be updated.");
        event.setName(request.name().trim());
        event.setDescription(request.description().trim());
        event.setStartDate(request.startDate());
        eventRepository.save(event);
        return getEvent(id);
    }

    @Transactional
    public void deleteEvent(Long id) {
        Event event = loadEvent(id);
        requireState(event, EventState.TENTATIVE, "Only tentative events can be deleted.");
        eventRepository.delete(event);
    }

    @Transactional
    public EventDetailResponse addArtistToEvent(Long eventId, AddArtistToEventRequest request) {
        Event event = loadEventWithArtists(eventId);
        requireState(event, EventState.TENTATIVE, "Artists can only be changed while the event is tentative.");
        Artist artist =
                artistRepository
                        .findById(request.artistId())
                        .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + request.artistId()));
        if (!artist.isActive()) {
            throw new BusinessRuleException("Inactive artists cannot be added to events.");
        }
        if (event.getArtists().stream().anyMatch(a -> a.getId().equals(artist.getId()))) {
            throw new BusinessRuleException("Artist is already assigned to this event.");
        }
        event.getArtists().add(artist);
        eventRepository.save(event);
        return getEvent(eventId);
    }

    @Transactional
    public EventDetailResponse removeArtistFromEvent(Long eventId, Long artistId) {
        Event event = loadEventWithArtists(eventId);
        requireState(event, EventState.TENTATIVE, "Artists can only be changed while the event is tentative.");
        if (!artistRepository.existsById(artistId)) {
            throw new ResourceNotFoundException("Artist not found: " + artistId);
        }
        boolean removed = event.getArtists().removeIf(a -> a.getId().equals(artistId));
        if (!removed) {
            throw new BusinessRuleException("Artist is not assigned to this event.");
        }
        eventRepository.save(event);
        return getEvent(eventId);
    }

    @Transactional
    public EventDetailResponse confirmEvent(Long id) {
        Event event = loadEvent(id);
        requireState(event, EventState.TENTATIVE, "Only tentative events can be confirmed.");
        LocalDateTime now = LocalDateTime.now(clock);
        if (!event.getStartDate().isAfter(now)) {
            throw new BusinessRuleException("Only events with a future start date can be confirmed.");
        }
        event.setState(EventState.CONFIRMED);
        eventRepository.save(event);
        return getEvent(id);
    }

    @Transactional
    public EventDetailResponse rescheduleEvent(Long id, RescheduleEventRequest request) {
        Event event = loadEvent(id);
        if (event.getState() != EventState.CONFIRMED && event.getState() != EventState.RESCHEDULED) {
            throw new BusinessRuleException("Only confirmed or rescheduled events can be rescheduled.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate eventDay = event.getStartDate().atZone(ZoneId.systemDefault()).toLocalDate();
        if (eventDay.isBefore(today)) {
            throw new BusinessRuleException("Cannot reschedule an event that has already occurred.");
        }
        LocalDateTime newStart = request.startDate();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!newStart.isAfter(now)) {
            throw new BusinessRuleException("The new start date must be in the future.");
        }
        event.setStartDate(newStart);
        event.setState(EventState.RESCHEDULED);
        eventRepository.save(event);
        return getEvent(id);
    }

    @Transactional
    public EventDetailResponse cancelEvent(Long id) {
        Event event = loadEvent(id);
        if (event.getState() != EventState.CONFIRMED && event.getState() != EventState.RESCHEDULED) {
            throw new BusinessRuleException("Only confirmed or rescheduled events can be cancelled.");
        }
        event.setState(EventState.CANCELLED);
        eventRepository.save(event);
        return getEvent(id);
    }

    private Event loadEvent(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }

    private Event loadEventWithArtists(Long id) {
        return eventRepository
                .findWithArtistsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }

    private static void requireState(Event event, EventState expected, String message) {
        if (event.getState() != expected) {
            throw new BusinessRuleException(message);
        }
    }
}
