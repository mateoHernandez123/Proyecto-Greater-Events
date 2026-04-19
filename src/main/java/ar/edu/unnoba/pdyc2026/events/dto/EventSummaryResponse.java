package ar.edu.unnoba.pdyc2026.events.dto;

import ar.edu.unnoba.pdyc2026.events.model.EventState;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record EventSummaryResponse(
        Long id,
        String name,
        @JsonProperty("start_date") LocalDateTime startDate,
        EventState state,
        @JsonProperty("artist_count") int artistCount) {}
