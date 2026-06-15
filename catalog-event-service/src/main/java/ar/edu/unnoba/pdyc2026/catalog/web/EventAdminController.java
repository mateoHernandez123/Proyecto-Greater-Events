package ar.edu.unnoba.pdyc2026.catalog.web;

import ar.edu.unnoba.pdyc2026.catalog.dto.AddArtistToEventRequest;
import ar.edu.unnoba.pdyc2026.catalog.dto.EventCreateRequest;
import ar.edu.unnoba.pdyc2026.catalog.dto.EventDetailResponse;
import ar.edu.unnoba.pdyc2026.catalog.dto.EventUpdateRequest;
import ar.edu.unnoba.pdyc2026.catalog.dto.RescheduleEventRequest;
import ar.edu.unnoba.pdyc2026.catalog.service.EventService;
import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.common.model.EventState;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/events")
public class EventAdminController {

    private final EventService eventService;

    public EventAdminController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventSummaryResponse> listEvents(@RequestParam(required = false) EventState state) {
        return eventService.listEvents(Optional.ofNullable(state));
    }

    @GetMapping("/{id}")
    public EventDetailResponse getEvent(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse createEvent(@Valid @RequestBody EventCreateRequest request) {
        return eventService.createEvent(request);
    }

    @PutMapping("/{id}")
    public EventDetailResponse updateEvent(@PathVariable Long id, @Valid @RequestBody EventUpdateRequest request) {
        return eventService.updateEventDetails(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
    }

    @PostMapping("/{id}/artists")
    public EventDetailResponse addArtist(
            @PathVariable Long id, @Valid @RequestBody AddArtistToEventRequest request) {
        return eventService.addArtistToEvent(id, request);
    }

    @DeleteMapping("/{id}/artists/{artistId}")
    public EventDetailResponse removeEventArtist(
            @PathVariable("id") Long eventId, @PathVariable Long artistId) {
        return eventService.removeArtistFromEvent(eventId, artistId);
    }

    @PutMapping("/{id}/confirmed")
    public EventDetailResponse confirmEvent(@PathVariable Long id) {
        return eventService.confirmEvent(id);
    }

    @PutMapping("/{id}/rescheduled")
    public EventDetailResponse rescheduleEvent(
            @PathVariable Long id, @Valid @RequestBody RescheduleEventRequest request) {
        return eventService.rescheduleEvent(id, request);
    }

    @PutMapping("/{id}/canceled")
    public EventDetailResponse cancelEvent(@PathVariable Long id) {
        return eventService.cancelEvent(id);
    }
}
