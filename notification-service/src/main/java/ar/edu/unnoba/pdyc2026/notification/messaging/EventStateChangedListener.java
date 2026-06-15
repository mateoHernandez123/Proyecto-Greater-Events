package ar.edu.unnoba.pdyc2026.notification.messaging;

import ar.edu.unnoba.pdyc2026.common.messaging.EventStateChangedMessage;
import ar.edu.unnoba.pdyc2026.notification.config.RabbitConfig;
import ar.edu.unnoba.pdyc2026.notification.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class EventStateChangedListener {

    private final NotificationService notificationService;

    public EventStateChangedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.EVENT_STATE_QUEUE)
    public void onEventStateChanged(EventStateChangedMessage message) {
        notificationService.handleEventStateChanged(message);
    }
}
