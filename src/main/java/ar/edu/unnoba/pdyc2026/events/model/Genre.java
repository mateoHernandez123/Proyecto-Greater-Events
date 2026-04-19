package ar.edu.unnoba.pdyc2026.events.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Genre {
    ROCK("rock"),
    TECHNO("techno"),
    POP("pop"),
    JAZZ("jazz"),
    FOLK("folk");

    private final String apiValue;

    Genre(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    @JsonCreator
    public static Genre fromApi(String value) {
        if (value == null) {
            return null;
        }
        for (Genre g : values()) {
            if (g.apiValue.equalsIgnoreCase(value)) {
                return g;
            }
        }
        throw new IllegalArgumentException("Unknown genre: " + value);
    }
}
