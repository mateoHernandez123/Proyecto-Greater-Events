package ar.edu.unnoba.pdyc2026.usersocial.web;

import ar.edu.unnoba.pdyc2026.common.dto.NotificationRecipientDto;
import ar.edu.unnoba.pdyc2026.usersocial.service.NotificationRecipientService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationRecipientService notificationRecipientService;

    public InternalNotificationController(NotificationRecipientService notificationRecipientService) {
        this.notificationRecipientService = notificationRecipientService;
    }

    @GetMapping("/recipients")
    public List<NotificationRecipientDto> recipients(
            @RequestParam Long eventId, @RequestParam(required = false) List<Long> artistIds) {
        return notificationRecipientService.findRecipients(eventId, artistIds);
    }
}
