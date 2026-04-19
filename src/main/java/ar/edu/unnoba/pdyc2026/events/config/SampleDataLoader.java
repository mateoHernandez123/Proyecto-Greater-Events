package ar.edu.unnoba.pdyc2026.events.config;

import ar.edu.unnoba.pdyc2026.events.model.Artist;
import ar.edu.unnoba.pdyc2026.events.model.Event;
import ar.edu.unnoba.pdyc2026.events.model.EventState;
import ar.edu.unnoba.pdyc2026.events.model.Genre;
import ar.edu.unnoba.pdyc2026.events.repository.ArtistRepository;
import ar.edu.unnoba.pdyc2026.events.repository.EventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga artistas y eventos de ejemplo la primera vez que la base está vacía.
 * No se ejecuta con el perfil {@code test} (tests usan H2 aislado).
 */
@Component
@Profile("!test")
@Order
public class SampleDataLoader implements CommandLineRunner {

    private final ArtistRepository artistRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    public SampleDataLoader(ArtistRepository artistRepository, EventRepository eventRepository, Clock clock) {
        this.artistRepository = artistRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (artistRepository.count() > 0) {
            return;
        }

        LocalDateTime t = LocalDateTime.now(clock);

        Artist a1 = artist("Los Planetarios", Genre.ROCK);
        Artist a2 = artist("DJ Nebula", Genre.TECHNO);
        Artist a3 = artist("Luna Martínez", Genre.POP);
        Artist a4 = artist("Cuarteto Sur", Genre.JAZZ);
        Artist a5 = artist("Raíces del Norte", Genre.FOLK);
        Artist a6 = artist("The Retirees", Genre.ROCK);
        artistRepository.save(a1);
        artistRepository.save(a2);
        artistRepository.save(a3);
        artistRepository.save(a4);
        artistRepository.save(a5);
        artistRepository.save(a6);

        Event evTentative = event(
                "Jazz al Parque — edición borrador",
                "Organización en curso; grilla tentativa.",
                t.plusMonths(2).withHour(18).withMinute(0).withSecond(0).withNano(0),
                EventState.TENTATIVE);
        evTentative.getArtists().add(a4);
        evTentative.getArtists().add(a1);
        eventRepository.save(evTentative);

        Event evConfirmed = event(
                "Rock en el río 2026",
                "Festival al aire libre. Entradas a la venta.",
                t.plusMonths(4).withHour(16).withMinute(0).withSecond(0).withNano(0),
                EventState.CONFIRMED);
        evConfirmed.getArtists().add(a1);
        evConfirmed.getArtists().add(a3);
        eventRepository.save(evConfirmed);

        Event evRescheduled = event(
                "Techno Night",
                "Fecha movida por licitación del venue.",
                t.plusMonths(3).withHour(23).withMinute(0).withSecond(0).withNano(0),
                EventState.RESCHEDULED);
        evRescheduled.getArtists().add(a2);
        eventRepository.save(evRescheduled);

        Event evCancelled = event(
                "Festival folklore invierno",
                "Cancelado por condiciones climáticas.",
                t.plusMonths(1).withHour(15).withMinute(0).withSecond(0).withNano(0),
                EventState.CANCELLED);
        evCancelled.getArtists().add(a5);
        eventRepository.save(evCancelled);

        Event evPastLineup = event(
                "Show acústico (histórico)",
                "Evento de ejemplo con artista luego desactivado.",
                t.minusMonths(6).withHour(20).withMinute(0).withSecond(0).withNano(0),
                EventState.CANCELLED);
        evPastLineup.getArtists().add(a6);
        eventRepository.save(evPastLineup);
        a6.setActive(false);
        artistRepository.save(a6);
    }

    private static Artist artist(String name, Genre genre) {
        Artist a = new Artist();
        a.setName(name);
        a.setGenre(genre);
        a.setActive(true);
        return a;
    }

    private static Event event(String name, String description, LocalDateTime start, EventState state) {
        Event e = new Event();
        e.setName(name);
        e.setDescription(description);
        e.setStartDate(start);
        e.setState(state);
        return e;
    }
}
