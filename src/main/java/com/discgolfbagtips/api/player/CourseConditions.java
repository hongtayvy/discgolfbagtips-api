package com.discgolfbagtips.api.player;

import jakarta.validation.constraints.NotNull;

public record CourseConditions(@NotNull WeatherCondition weather) {

    public String describe() {
        return "Conditions: %s. %s".formatted(weather.description(), weather.guidance());
    }
}
