package ar.edu.unnoba.pdyc2026.catalog.dto;

import ar.edu.unnoba.pdyc2026.common.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record EventDetailResponse(
        Long id,
        String name,
        @JsonProperty("start_date") LocalDateTime startDate,
        String description,
        EventState state,
        List<ArtistResponse> artists) {}
