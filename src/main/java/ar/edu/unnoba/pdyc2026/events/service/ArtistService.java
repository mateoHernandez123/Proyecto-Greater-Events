package ar.edu.unnoba.pdyc2026.events.service;

import ar.edu.unnoba.pdyc2026.events.dto.ArtistCreateRequest;
import ar.edu.unnoba.pdyc2026.events.dto.ArtistResponse;
import ar.edu.unnoba.pdyc2026.events.dto.ArtistUpdateRequest;
import ar.edu.unnoba.pdyc2026.events.exception.BusinessRuleException;
import ar.edu.unnoba.pdyc2026.events.exception.ResourceNotFoundException;
import ar.edu.unnoba.pdyc2026.events.model.Artist;
import ar.edu.unnoba.pdyc2026.events.model.Genre;
import ar.edu.unnoba.pdyc2026.events.repository.ArtistRepository;
import ar.edu.unnoba.pdyc2026.events.repository.EventRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final EventRepository eventRepository;

    public ArtistService(ArtistRepository artistRepository, EventRepository eventRepository) {
        this.artistRepository = artistRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<ArtistResponse> listArtists(Optional<Genre> genre) {
        List<Artist> artists =
                genre.map(artistRepository::findAllByGenre).orElseGet(artistRepository::findAll);
        return artists.stream().map(ArtistService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ArtistResponse getArtist(Long id) {
        return artistRepository.findById(id).map(ArtistService::toResponse).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public ArtistResponse createArtist(ArtistCreateRequest request) {
        Artist artist = new Artist();
        artist.setName(request.name().trim());
        artist.setGenre(request.genre());
        artist.setActive(true);
        return toResponse(artistRepository.save(artist));
    }

    @Transactional
    public ArtistResponse updateArtist(Long id, ArtistUpdateRequest request) {
        Artist artist = artistRepository.findById(id).orElseThrow(() -> notFound(id));
        if (eventRepository.existsByArtists_Id(id)) {
            throw new BusinessRuleException(
                    "Artist has been assigned to events and cannot be modified; deactivate instead.");
        }
        artist.setName(request.name().trim());
        artist.setGenre(request.genre());
        return toResponse(artistRepository.save(artist));
    }

    @Transactional
    public Optional<ArtistResponse> deleteArtist(Long id) {
        Artist artist = artistRepository.findById(id).orElseThrow(() -> notFound(id));
        if (eventRepository.existsByArtists_Id(id)) {
            artist.setActive(false);
            return Optional.of(toResponse(artistRepository.save(artist)));
        }
        artistRepository.delete(artist);
        return Optional.empty();
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Artist not found: " + id);
    }

    private static ArtistResponse toResponse(Artist a) {
        return new ArtistResponse(a.getId(), a.getName(), a.getGenre(), a.isActive());
    }
}
