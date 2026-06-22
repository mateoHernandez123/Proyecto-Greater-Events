package ar.edu.unnoba.pdyc2026.usersocial.client;

import ar.edu.unnoba.pdyc2026.common.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.usersocial.config.FeignConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "catalog-event-service", configuration = FeignConfig.class)
public interface CatalogClient {

    @GetMapping("/internal/artists/{id}")
    ArtistResponse getArtist(@PathVariable("id") Long id);

    @GetMapping("/internal/events/{id}")
    EventSummaryResponse getEvent(@PathVariable("id") Long id);

    @GetMapping("/internal/events/upcoming")
    List<EventSummaryResponse> getUpcomingEvents(@RequestParam("artistIds") String artistIds);
}
