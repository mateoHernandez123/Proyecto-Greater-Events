package ar.edu.unnoba.pdyc2026.events.web;

import ar.edu.unnoba.pdyc2026.events.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.events.dto.EventSummaryResponse;
import ar.edu.unnoba.pdyc2026.events.dto.FollowArtistRequest;
import ar.edu.unnoba.pdyc2026.events.service.EndUserService;
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

/** Operaciones sobre los artistas que sigue el usuario autenticado (TP4). */
@RestController
@RequestMapping("/me/following")
public class MeFollowingController {

    private final EndUserService endUserService;

    public MeFollowingController(EndUserService endUserService) {
        this.endUserService = endUserService;
    }

    @GetMapping
    public List<ArtistResponse> listFollowing() {
        return endUserService.listFollowing();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArtistResponse followArtist(@Valid @RequestBody FollowArtistRequest request) {
        return endUserService.followArtist(request.artistId());
    }

    @DeleteMapping("/{artistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollowArtist(@PathVariable Long artistId) {
        endUserService.unfollowArtist(artistId);
    }

    @GetMapping("/events")
    public List<EventSummaryResponse> upcomingForFollowedArtists() {
        return endUserService.listUpcomingEventsForFollowedArtists();
    }
}
