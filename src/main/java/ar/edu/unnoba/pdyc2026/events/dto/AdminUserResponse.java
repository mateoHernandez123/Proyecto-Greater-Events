package ar.edu.unnoba.pdyc2026.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminUserResponse(
        String id,
        String username,
        String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        Boolean enabled) {}
