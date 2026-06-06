package ar.edu.unnoba.pdyc2026.events.model;

/** Por que un usuario es destinatario de una notificacion. */
public enum NotificationReason {
    /** El evento esta marcado como favorito por el usuario. */
    FAVORITE_EVENT,
    /** El evento incluye al menos un artista seguido por el usuario. */
    FOLLOWED_ARTIST
}
