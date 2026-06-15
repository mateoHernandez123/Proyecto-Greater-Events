package ar.edu.unnoba.pdyc2026.catalog.web;

import ar.edu.unnoba.pdyc2026.catalog.service.PublicCatalogService;
import ar.edu.unnoba.pdyc2026.common.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
