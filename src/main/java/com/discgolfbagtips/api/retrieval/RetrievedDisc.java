package com.discgolfbagtips.api.retrieval;

import com.discgolfbagtips.api.analysis.FlightProfile;
import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.StabilityClass;
import java.util.List;

/** A candidate returned by the vector store, carrying everything needed to explain the match. */
public record RetrievedDisc(
        String discId,
        String name,
        String brand,
        String category,
        double speed,
        double glide,
        double turn,
        double fade,
        String stabilityLabel,
        String imageUrl,
        String sourceUrl,
        String passage,
        List<String> descriptors,
        String model,
        double similarity,
        double distance,
        int rank) {

    public FlightProfile flight() {
        return new FlightProfile(speed, glide, turn, fade);
    }

    public StabilityClass stabilityClass() {
        return StabilityClass.forIndex(turn + fade);
    }

    public DiscSlot slot() {
        return DiscSlot.forSpeed(speed);
    }

    public String displayName() {
        return brand + " " + name;
    }

    public RetrievedDisc withRank(int newRank) {
        return new RetrievedDisc(discId, name, brand, category, speed, glide, turn, fade, stabilityLabel,
                imageUrl, sourceUrl, passage, descriptors, model, similarity, distance, newRank);
    }

    /** A short excerpt of the embedded passage, for the explainability payload. */
    public String passageExcerpt(int maxChars) {
        if (passage == null) {
            return "";
        }
        return passage.length() <= maxChars ? passage : passage.substring(0, maxChars).trim() + "…";
    }
}
