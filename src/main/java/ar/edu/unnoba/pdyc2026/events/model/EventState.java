package ar.edu.unnoba.pdyc2026.events.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EventState {
    TENTATIVE("tentative"),
    CONFIRMED("confirmed"),
    RESCHEDULED("rescheduled"),
    CANCELLED("cancelled");

    private final String apiValue;

    EventState(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    @JsonCreator
    public static EventState fromApi(String value) {
        if (value == null) {
            return null;
        }
        for (EventState s : values()) {
            if (s.apiValue.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown event state: " + value);
    }
}
