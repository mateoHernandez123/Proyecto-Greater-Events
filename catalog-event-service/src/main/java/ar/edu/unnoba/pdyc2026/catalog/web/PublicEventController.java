package ar.edu.unnoba.pdyc2026.catalog.web;

import ar.edu.unnoba.pdyc2026.catalog.dto.EventDetailResponse;
import ar.edu.unnoba.pdyc2026.catalog.service.PublicCatalogService;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class PublicEventController {

    private final PublicCatalogService catalogService;

    public PublicEventController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<EventSummaryResponse> listUpcomingEvents() {
        return catalogService.listUpcomingEvents();
    }

    @GetMapping("/{id}")
    public EventDetailResponse getEvent(@PathVariable Long id) {
        return catalogService.getEvent(id);
    }
}
