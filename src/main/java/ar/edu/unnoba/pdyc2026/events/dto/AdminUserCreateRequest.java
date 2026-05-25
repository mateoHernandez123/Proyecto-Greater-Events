package ar.edu.unnoba.pdyc2026.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminUserCreateRequest(
        @NotBlank String username,
        @NotBlank String password,
        @Email String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        Boolean enabled,
        @JsonProperty("temporary_password") Boolean temporaryPassword) {}
