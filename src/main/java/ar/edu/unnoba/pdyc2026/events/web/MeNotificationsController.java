package ar.edu.unnoba.pdyc2026.events.web;

import ar.edu.unnoba.pdyc2026.events.dto.NotificationResponse;
import ar.edu.unnoba.pdyc2026.events.service.NotificationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectura y marcado de notificaciones del usuario autenticado (TP4).
 *
 * <p>El sistema dispara una notificacion automaticamente cuando un evento que el
 * usuario marco como favorito o que tiene a un artista que sigue cambia a
 * {@code CONFIRMED}, {@code RESCHEDULED} o {@code CANCELLED}. Ver
 * {@link ar.edu.unnoba.pdyc2026.events.service.NotificationService}.
 */
@RestController
@RequestMapping("/me/notifications")
public class MeNotificationsController {

    private final NotificationService notificationService;

    public MeNotificationsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> listNotifications(
            @RequestParam(name = "unread_only", defaultValue = "false") boolean unreadOnly) {
        return notificationService.listMyNotifications(unreadOnly);
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }
}
