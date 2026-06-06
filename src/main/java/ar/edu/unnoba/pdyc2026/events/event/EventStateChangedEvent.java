package ar.edu.unnoba.pdyc2026.events.event;

import ar.edu.unnoba.pdyc2026.events.model.EventState;

/**
 * Evento de dominio publicado por {@code EventService} cuando un evento musical pasa
 * a {@code CONFIRMED}, {@code RESCHEDULED} o {@code CANCELLED}. Lo consumen los listeners
 * asincronicos que generan notificaciones a usuarios finales (favorito, follower).
 */
public record EventStateChangedEvent(Long eventId, EventState newState) {}
