package ar.edu.unnoba.pdyc2026.events.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Notificacion persistida para un usuario final (TP4). Se genera de forma asincronica
 * cada vez que un evento favorito o de un artista seguido cambia de estado a
 * confirmed, rescheduled o cancelled.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at DESC"),
            @Index(name = "idx_notifications_user_read", columnList = "user_id, is_read")
        })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** Razon por la que el usuario recibe la notificacion. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationReason reason;

    /** Estado al que paso el evento (confirmed, rescheduled, cancelled). */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", nullable = false, length = 32)
    private EventState newState;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Columna mapeada explicitamente como `is_read` para evitar conflictos con `READ`,
    // que es palabra reservada en varios dialectos SQL.
    @Column(name = "is_read", nullable = false)
    private boolean read;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public NotificationReason getReason() {
        return reason;
    }

    public void setReason(NotificationReason reason) {
        this.reason = reason;
    }

    public EventState getNewState() {
        return newState;
    }

    public void setNewState(EventState newState) {
        this.newState = newState;
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
