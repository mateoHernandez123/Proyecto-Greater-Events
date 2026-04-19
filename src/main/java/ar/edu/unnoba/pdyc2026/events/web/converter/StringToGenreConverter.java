package ar.edu.unnoba.pdyc2026.events.web.converter;

import ar.edu.unnoba.pdyc2026.events.model.Genre;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToGenreConverter implements Converter<String, Genre> {

    @Override
    public Genre convert(String source) {
        return Genre.fromApi(source);
    }
}
