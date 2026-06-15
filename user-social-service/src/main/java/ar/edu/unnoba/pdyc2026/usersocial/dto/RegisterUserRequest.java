package ar.edu.unnoba.pdyc2026.usersocial.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 6, max = 128) String password,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName) {}
