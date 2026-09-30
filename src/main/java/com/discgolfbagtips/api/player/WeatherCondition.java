package com.discgolfbagtips.api.player;

import com.discgolfbagtips.api.embedding.FlightTerminology;

public enum WeatherCondition {

    HOT("hot weather", 0.3),
    NORMAL("normal conditions", 0.0),
    RAINY("rain and wet grips", 0.0),
    COLD("cold, dense air", -0.4),
    WINDY("windy conditions", 0.8);

    private final String description;
    /** How much extra stability the conditions call for, in stability-index points. */
    private final double stabilityBias;

    WeatherCondition(String description, double stabilityBias) {
        this.description = description;
        this.stabilityBias = stabilityBias;
    }

    public String description() {
        return description;
    }

    public double stabilityBias() {
        return stabilityBias;
    }

    public String guidance() {
        return FlightTerminology.conditionEffect(name());
    }

    /** Rain is the one condition where the plastic matters more than the flight numbers. */
    public boolean gripCritical() {
        return this == RAINY || this == COLD;
    }
}
