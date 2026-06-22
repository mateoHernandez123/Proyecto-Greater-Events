package ar.edu.unnoba.pdyc2026.common.messaging;

import ar.edu.unnoba.pdyc2026.common.model.EventState;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EventStateChangedMessage(
        @JsonProperty("event_id") Long eventId,
        @JsonProperty("event_name") String eventName,
        @JsonProperty("previous_state") EventState previousState,
        @JsonProperty("current_state") EventState currentState,
        @JsonProperty("artist_ids") List<Long> artistIds) {}
