package com.discgolfbagtips.api.analysis;

/** A four-number flight line, used both for real discs and for the ideal disc a gap calls for. */
public record FlightProfile(double speed, double glide, double turn, double fade) {

    public double stabilityIndex() {
        return turn + fade;
    }

    public FlightProfile minus(FlightProfile other) {
        return new FlightProfile(
                round(speed - other.speed),
                round(glide - other.glide),
                round(turn - other.turn),
                round(fade - other.fade));
    }

    /** Straight-line distance in flight-number space; used to find the closest disc already bagged. */
    public double distanceTo(FlightProfile other) {
        double ds = speed - other.speed;
        double dg = glide - other.glide;
        double dt = turn - other.turn;
        double df = fade - other.fade;
        return Math.sqrt(ds * ds + dg * dg + dt * dt + df * df);
    }

    public String format() {
        return "%s / %s / %s / %s".formatted(trim(speed), trim(glide), trim(turn), trim(fade));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(round(value));
    }
}
