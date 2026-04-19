package ar.edu.unnoba.pdyc2026.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record EventUpdateRequest(
        @NotBlank String name,
        @NotNull @JsonProperty("start_date") LocalDateTime startDate,
        @NotBlank String description) {}
