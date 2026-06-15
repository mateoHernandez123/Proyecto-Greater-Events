package ar.edu.unnoba.pdyc2026.catalog.repository;

import ar.edu.unnoba.pdyc2026.catalog.model.Artist;
import ar.edu.unnoba.pdyc2026.common.model.Genre;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    List<Artist> findAllByGenre(Genre genre);

    List<Artist> findAllByActiveTrue();
}
