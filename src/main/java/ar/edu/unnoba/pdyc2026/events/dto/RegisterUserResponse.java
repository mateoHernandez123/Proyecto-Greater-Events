package ar.edu.unnoba.pdyc2026.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterUserResponse(
        Long id,
        @JsonProperty("keycloak_id") String keycloakId,
        String username,
        String email) {}
