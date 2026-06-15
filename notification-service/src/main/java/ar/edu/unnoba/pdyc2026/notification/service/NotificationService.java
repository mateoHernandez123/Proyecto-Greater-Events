package ar.edu.unnoba.pdyc2026.notification.service;

import ar.edu.unnoba.pdyc2026.common.dto.NotificationRecipientDto;
import ar.edu.unnoba.pdyc2026.common.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.common.messaging.EventStateChangedMessage;
import ar.edu.unnoba.pdyc2026.common.messaging.NotificationReason;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import ar.edu.unnoba.pdyc2026.notification.client.UserSocialClient;
import ar.edu.unnoba.pdyc2026.notification.dto.NotificationResponse;
import ar.edu.unnoba.pdyc2026.notification.model.Notification;
import ar.edu.unnoba.pdyc2026.notification.model.NotificationUser;
import ar.edu.unnoba.pdyc2026.notification.repository.NotificationRepository;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final UserSocialClient userSocialClient;

    public NotificationService(
            NotificationRepository notificationRepository,
            CurrentUserService currentUserService,
            UserSocialClient userSocialClient) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
        this.userSocialClient = userSocialClient;
    }

    @Transactional
    public void handleEventStateChanged(EventStateChangedMessage message) {
        List<Long> artistIds =
                message.artistIds() != null ? message.artistIds() : Collections.emptyList();
        List<NotificationRecipientDto> recipients =
                userSocialClient.getRecipients(message.eventId(), artistIds);
        for (NotificationRecipientDto recipient : recipients) {
            persistNotification(message, recipient.keycloakId(), recipient.reason());
        }
        log.info(
                "Dispatched notifications for event {} -> {} (users: {})",
                message.eventId(),
                message.currentState(),
                recipients.size());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMyNotifications(boolean unreadOnly) {
        NotificationUser user = currentUserService.getOrProvisionCurrentUser();
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserKeycloakIdAndReadFalseOrderByCreatedAtDesc(
                        user.getKeycloakId())
                : notificationRepository.findByUserKeycloakIdOrderByCreatedAtDesc(user.getKeycloakId());
        return notifications.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId) {
        NotificationUser user = currentUserService.getOrProvisionCurrentUser();
        Notification notification = requireOwnedNotification(notificationId, user.getKeycloakId());
        notification.setRead(true);
        return toResponse(notification);
    }

    @Transactional
    public void delete(Long notificationId) {
        NotificationUser user = currentUserService.getOrProvisionCurrentUser();
        Notification notification = requireOwnedNotification(notificationId, user.getKeycloakId());
        notificationRepository.delete(notification);
    }

    private Notification requireOwnedNotification(Long notificationId, String keycloakId) {
        return notificationRepository
                .findById(notificationId)
                .filter(n -> n.getUserKeycloakId().equals(keycloakId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
    }

    private void persistNotification(
            EventStateChangedMessage message, String userKeycloakId, NotificationReason reason) {
        Notification notification = new Notification();
        notification.setUserKeycloakId(userKeycloakId);
        notification.setEventId(message.eventId());
        notification.setEventName(message.eventName());
        notification.setReason(reason);
        notification.setPreviousState(message.previousState());
        notification.setCurrentState(message.currentState());
        notification.setMessage(messageFor(message.eventName(), reason, message.previousState(), message.currentState()));
        notificationRepository.save(notification);
    }

    private static String messageFor(
            String eventName, NotificationReason reason, EventState previousState, EventState currentState) {
        String reasonLabel = switch (reason) {
            case FAVORITE_EVENT -> "your favorite event";
            case FOLLOWED_ARTIST -> "an event with an artist you follow";
        };
        return "Update on "
                + reasonLabel
                + ": '"
                + eventName
                + "' changed from "
                + previousState.getApiValue()
                + " to "
                + currentState.getApiValue()
                + ".";
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventId(),
                notification.getEventName(),
                notification.getReason(),
                notification.getPreviousState(),
                notification.getCurrentState(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.isRead());
    }
}
