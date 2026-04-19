package ar.edu.unnoba.pdyc2026.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RescheduleEventRequest(@NotNull @JsonProperty("start_date") LocalDateTime startDate) {}
