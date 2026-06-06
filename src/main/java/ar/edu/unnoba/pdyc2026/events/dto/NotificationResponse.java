package ar.edu.unnoba.pdyc2026.events.dto;

import ar.edu.unnoba.pdyc2026.events.model.EventState;
import ar.edu.unnoba.pdyc2026.events.model.NotificationReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        @JsonProperty("event_id") Long eventId,
        @JsonProperty("event_name") String eventName,
        NotificationReason reason,
        @JsonProperty("new_state") EventState newState,
        String message,
        @JsonProperty("created_at") Instant createdAt,
        boolean read) {}
