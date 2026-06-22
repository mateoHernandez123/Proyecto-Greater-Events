package ar.edu.unnoba.pdyc2026.usersocial.web;

import ar.edu.unnoba.pdyc2026.common.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.usersocial.dto.FavoriteEventRequest;
import ar.edu.unnoba.pdyc2026.usersocial.service.EndUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/favorite-events")
public class MeFavoriteEventsController {

    private final EndUserService endUserService;

    public MeFavoriteEventsController(EndUserService endUserService) {
        this.endUserService = endUserService;
    }

    @GetMapping
    public List<EventSummaryResponse> listFavorites() {
        return endUserService.listFavoriteEvents();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventSummaryResponse addFavorite(@Valid @RequestBody FavoriteEventRequest request) {
        return endUserService.favoriteEvent(request.eventId());
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@PathVariable Long eventId) {
        endUserService.removeFavoriteEvent(eventId);
    }
}
