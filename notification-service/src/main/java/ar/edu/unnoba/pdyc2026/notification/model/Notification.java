package ar.edu.unnoba.pdyc2026.notification.model;

import ar.edu.unnoba.pdyc2026.common.model.EventState;
import ar.edu.unnoba.pdyc2026.common.messaging.NotificationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "idx_notifications_user_created", columnList = "user_keycloak_id, created_at DESC"),
            @Index(name = "idx_notifications_user_read", columnList = "user_keycloak_id, is_read")
        })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_keycloak_id", nullable = false, length = 64)
    private String userKeycloakId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_name", nullable = false, length = 255)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", nullable = false, length = 32)
    private EventState previousState;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 32)
    private EventState currentState;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "is_read", nullable = false)
    private boolean read;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserKeycloakId() {
        return userKeycloakId;
    }

    public void setUserKeycloakId(String userKeycloakId) {
        this.userKeycloakId = userKeycloakId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public NotificationReason getReason() {
        return reason;
    }

    public void setReason(NotificationReason reason) {
        this.reason = reason;
    }

    public EventState getPreviousState() {
        return previousState;
    }

    public void setPreviousState(EventState previousState) {
        this.previousState = previousState;
    }

    public EventState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(EventState currentState) {
        this.currentState = currentState;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
