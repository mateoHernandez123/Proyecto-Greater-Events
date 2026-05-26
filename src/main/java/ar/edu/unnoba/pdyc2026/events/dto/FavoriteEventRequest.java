package ar.edu.unnoba.pdyc2026.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record FavoriteEventRequest(@NotNull @JsonProperty("event_id") Long eventId) {}
