package ar.edu.unnoba.pdyc2026.catalog.messaging;

import ar.edu.unnoba.pdyc2026.catalog.config.RabbitConfig;
import ar.edu.unnoba.pdyc2026.catalog.model.Artist;
import ar.edu.unnoba.pdyc2026.catalog.model.Event;
import ar.edu.unnoba.pdyc2026.common.messaging.EventStateChangedMessage;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventStateChangedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public EventStateChangedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Event event, EventState previousState) {
        List<Long> artistIds = event.getArtists().stream().map(Artist::getId).toList();
        rabbitTemplate.convertAndSend(
                RabbitConfig.EVENT_STATE_EXCHANGE,
                RabbitConfig.EVENT_STATE_ROUTING_KEY,
                new EventStateChangedMessage(
                        event.getId(),
                        event.getName(),
                        previousState,
                        event.getState(),
                        artistIds));
    }
}
