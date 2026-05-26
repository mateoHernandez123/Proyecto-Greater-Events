package ar.edu.unnoba.pdyc2026.events.service;

import ar.edu.unnoba.pdyc2026.events.dto.NotificationResponse;
import ar.edu.unnoba.pdyc2026.events.event.EventStateChangedEvent;
import ar.edu.unnoba.pdyc2026.events.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.events.model.Artist;
import ar.edu.unnoba.pdyc2026.events.model.Event;
import ar.edu.unnoba.pdyc2026.events.model.EventState;
import ar.edu.unnoba.pdyc2026.events.model.Notification;
import ar.edu.unnoba.pdyc2026.events.model.NotificationReason;
import ar.edu.unnoba.pdyc2026.events.model.User;
import ar.edu.unnoba.pdyc2026.events.repository.EventRepository;
import ar.edu.unnoba.pdyc2026.events.repository.NotificationRepository;
import ar.edu.unnoba.pdyc2026.events.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Genera y consulta notificaciones para usuarios finales (TP4).
 *
 * <p>Es a la vez el listener asincronico de {@link EventStateChangedEvent} (lado escritor)
 * y la fachada de consulta para los endpoints {@code /me/notifications} (lado lector).
 * Si el mismo usuario tiene el evento como favorito y ademas sigue a un artista del
 * lineup, recibe una sola notificacion priorizando {@code FAVORITE_EVENT}.
 *
 * <p>Importante: el handler escucha con {@link TransactionalEventListener} en fase
 * {@code AFTER_COMMIT}. Asi se garantiza que la lectura del {@code Event} desde
 * la base ve el nuevo estado y no la version pre-commit del publisher.
 * Ademas corre en otro hilo ({@code notificationExecutor}) para no bloquear al
 * cliente que disparo el cambio de estado.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(
            EventRepository eventRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            CurrentUserService currentUserService) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEventStateChanged(EventStateChangedEvent change) {
        Optional<Event> eventOpt = eventRepository.findWithArtistsById(change.eventId());
        if (eventOpt.isEmpty()) {
            log.warn("Event {} disappeared before notifications were dispatched", change.eventId());
            return;
        }
        Event event = eventOpt.get();
        Set<Long> artistIds = new HashSet<>();
        for (Artist a : event.getArtists()) {
            artistIds.add(a.getId());
        }

        Set<Long> notifiedUserIds = new HashSet<>();
        for (User user : userRepository.findByFavoriteEventId(event.getId())) {
            persistNotification(user, event, NotificationReason.FAVORITE_EVENT, change.newState());
            notifiedUserIds.add(user.getId());
        }

        if (!artistIds.isEmpty()) {
            for (User user : userRepository.findDistinctByFollowingArtistIdIn(artistIds)) {
                if (notifiedUserIds.add(user.getId())) {
                    persistNotification(user, event, NotificationReason.FOLLOWED_ARTIST, change.newState());
                }
            }
        }
        log.info(
                "Dispatched notifications for event {} -> {} (users: {})",
                event.getId(),
                change.newState(),
                notifiedUserIds.size());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMyNotifications(boolean unreadOnly) {
        User user = currentUserService.getOrProvisionCurrentUser();
        List<Notification> list = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId())
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return list.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId) {
        User user = currentUserService.getOrProvisionCurrentUser();
        Notification notification = notificationRepository
                .findById(notificationId)
                .filter(n -> n.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        notification.setRead(true);
        return toResponse(notification);
    }

    private void persistNotification(User user, Event event, NotificationReason reason, EventState newState) {
        Notification n = new Notification();
        n.setUser(user);
        n.setEvent(event);
        n.setReason(reason);
        n.setNewState(newState);
        n.setMessage(messageFor(event, reason, newState));
        notificationRepository.save(n);
    }

    private static String messageFor(Event event, NotificationReason reason, EventState newState) {
        String stateLabel = newState.name().toLowerCase();
        String reasonLabel = switch (reason) {
            case FAVORITE_EVENT -> "your favorite event";
            case FOLLOWED_ARTIST -> "an event with an artist you follow";
        };
        return "Update on " + reasonLabel + ": '" + event.getName() + "' is now " + stateLabel + ".";
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getEvent().getId(),
                n.getEvent().getName(),
                n.getReason(),
                n.getNewState(),
                n.getMessage(),
                n.getCreatedAt(),
                n.isRead());
    }
}
