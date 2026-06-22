package ar.edu.unnoba.pdyc2026.catalog.web;

import ar.edu.unnoba.pdyc2026.catalog.service.InternalCatalogService;
import ar.edu.unnoba.pdyc2026.common.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalCatalogController {

    private final InternalCatalogService internalCatalogService;

    public InternalCatalogController(InternalCatalogService internalCatalogService) {
        this.internalCatalogService = internalCatalogService;
    }

    @GetMapping("/artists/{id}")
    public ArtistResponse getArtist(@PathVariable Long id) {
        return internalCatalogService.getArtist(id);
    }

    @GetMapping("/events/{id}")
    public EventSummaryResponse getEvent(@PathVariable Long id) {
        return internalCatalogService.getEvent(id);
    }

    @GetMapping("/events/upcoming")
    public List<EventSummaryResponse> upcomingEvents(@RequestParam("artistIds") String artistIds) {
        return internalCatalogService.getUpcomingForArtistIds(artistIds);
    }
}
