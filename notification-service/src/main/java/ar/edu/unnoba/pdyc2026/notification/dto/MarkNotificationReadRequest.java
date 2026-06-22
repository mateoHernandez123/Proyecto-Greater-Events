package ar.edu.unnoba.pdyc2026.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarkNotificationReadRequest(@JsonProperty("is_read") Boolean isRead) {}
