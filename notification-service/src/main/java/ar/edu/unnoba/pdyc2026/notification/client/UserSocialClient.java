package ar.edu.unnoba.pdyc2026.notification.client;

import ar.edu.unnoba.pdyc2026.common.dto.NotificationRecipientDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-social-service")
public interface UserSocialClient {

    @GetMapping("/internal/notifications/recipients")
    List<NotificationRecipientDto> getRecipients(
            @RequestParam("eventId") Long eventId,
            @RequestParam(value = "artistIds", required = false) List<Long> artistIds);
}
