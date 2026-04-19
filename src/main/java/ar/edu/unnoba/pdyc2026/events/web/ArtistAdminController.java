package ar.edu.unnoba.pdyc2026.events.web;

import ar.edu.unnoba.pdyc2026.events.dto.ArtistCreateRequest;
import ar.edu.unnoba.pdyc2026.events.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.events.dto.ArtistUpdateRequest;
import ar.edu.unnoba.pdyc2026.events.model.Genre;
import ar.edu.unnoba.pdyc2026.events.service.ArtistService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/admin/artists")
public class ArtistAdminController {

    private final ArtistService artistService;

    public ArtistAdminController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @GetMapping
    public List<ArtistResponse> listArtists(@RequestParam(required = false) Genre genre) {
        return artistService.listArtists(Optional.ofNullable(genre));
    }

    @GetMapping("/{id}")
    public ArtistResponse getArtist(@PathVariable Long id) {
        return artistService.getArtist(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArtistResponse createArtist(@Valid @RequestBody ArtistCreateRequest request) {
        return artistService.createArtist(request);
    }

    @PutMapping("/{id}")
    public ArtistResponse updateArtist(@PathVariable Long id, @Valid @RequestBody ArtistUpdateRequest request) {
        return artistService.updateArtist(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ArtistResponse> removeArtist(@PathVariable Long id) {
        return artistService
                .deleteArtist(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
