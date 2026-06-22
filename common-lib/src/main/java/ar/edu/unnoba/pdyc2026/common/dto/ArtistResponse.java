package ar.edu.unnoba.pdyc2026.common.dto;

import ar.edu.unnoba.pdyc2026.common.model.Genre;

public record ArtistResponse(Long id, String name, Genre genre, boolean active) {}
