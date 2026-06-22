package ar.edu.unnoba.pdyc2026.common.dto;

import ar.edu.unnoba.pdyc2026.common.messaging.NotificationReason;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationRecipientDto(
        @JsonProperty("keycloak_id") String keycloakId, NotificationReason reason) {}
