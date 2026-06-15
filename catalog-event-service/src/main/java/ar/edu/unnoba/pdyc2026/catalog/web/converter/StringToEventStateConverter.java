package ar.edu.unnoba.pdyc2026.catalog.web.converter;

import ar.edu.unnoba.pdyc2026.common.model.EventState;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToEventStateConverter implements Converter<String, EventState> {

    @Override
    public EventState convert(String source) {
        return EventState.fromApi(source);
    }
}
