package ar.edu.unnoba.pdyc2026.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AddArtistToEventRequest(@NotNull @JsonProperty("artist_id") Long artistId) {}
