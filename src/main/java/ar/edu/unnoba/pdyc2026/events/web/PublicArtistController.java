package ar.edu.unnoba.pdyc2026.events.web;

import ar.edu.unnoba.pdyc2026.events.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.events.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.events.service.PublicCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints publicos del catalogo de artistas (TP4). No requieren token. */
@RestController
@RequestMapping("/artists")
public class PublicArtistController {

    private final PublicCatalogService catalogService;

    public PublicArtistController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<ArtistResponse> listActiveArtists() {
        return catalogService.listActiveArtists();
    }

    @GetMapping("/{artistId}/events")
    public List<EventSummaryResponse> upcomingEventsForArtist(@PathVariable Long artistId) {
        return catalogService.listUpcomingEventsForArtist(artistId);
    }
}
