package com.discgolfbagtips.api.analysis;

import com.discgolfbagtips.api.catalog.StabilityClass;
import java.util.ArrayList;
import java.util.List;

/**
 * How a disc's published stability became the stability the player actually sees, one term at a
 * time. Surfaced in the response so a surprising recommendation can be traced to the axis that
 * caused it rather than argued with.
 *
 * @param published {@code turn + fade} straight from the catalog
 * @param plastic   the blend's shift
 * @param weight    the shift from being lighter or heavier than the weight the numbers describe
 * @param wear      the shift from use, already damped by the plastic's durability
 */
public record StabilityBreakdown(double published, double plastic, double weight, double wear) {

    public double effective() {
        return round(published + plastic + weight + wear);
    }

    public double totalShift() {
        return round(plastic + weight + wear);
    }

    public StabilityClass effectiveClass() {
        return StabilityClass.forIndex(effective());
    }

    public StabilityClass publishedClass() {
        return StabilityClass.forIndex(published);
    }

    /** True when the adjustments moved the disc into a different stability class entirely. */
    public boolean movedClass() {
        return effectiveClass() != publishedClass();
    }

    /** Human-readable arithmetic, e.g. "2 published -0.3 plastic -0.4 weight -1.4 wear = -0.1". */
    public String explain() {
        List<String> terms = new ArrayList<>();
        terms.add(format(published) + " published");
        if (plastic != 0) {
            terms.add(signed(plastic) + " plastic");
        }
        if (weight != 0) {
            terms.add(signed(weight) + " weight");
        }
        if (wear != 0) {
            terms.add(signed(wear) + " wear");
        }
        return String.join(" ", terms) + " = " + format(effective());
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(round(value));
    }

    private static String signed(double value) {
        return (value > 0 ? "+" : "-") + format(Math.abs(value));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
