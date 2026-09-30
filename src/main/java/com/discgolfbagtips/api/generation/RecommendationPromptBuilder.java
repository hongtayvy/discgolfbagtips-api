package com.discgolfbagtips.api.generation;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Assembles the generation prompt. The retrieved passages are the only disc knowledge the model is
 * given, and it is told so — the point of the retrieval step is that the answer is grounded in our
 * catalog rather than in whatever the model remembers about disc golf.
 */
@Component
public class RecommendationPromptBuilder {

    private static final int PASSAGE_CHARS = 600;

    public String systemPrompt() {
        return """
                You are a disc golf bag consultant. You recommend exactly one disc for a player to add next.

                Hard rules:
                1. You may ONLY recommend a disc from the CANDIDATE DISCS list you are given. Never invent a mold,
                   and never recommend a disc from memory that is not in the list.
                2. Ground every claim in the flight numbers and description passages provided. Do not assert
                   facts about a disc that the passage does not support.
                3. Respect the player's skill level. Do not recommend a disc faster than they can throw.
                4. Explain the pick in terms of the specific gap in their bag and the conditions given.
                5. A disc's plastic, weight and wear change how it flies. The bag list gives you all three and
                   shows the arithmetic; treat the "plays" stability as the truth, not the published numbers.
                6. Write like a friendly local card-mate, not a marketing page. No hype, no exclamation marks.

                Respond with a single JSON object and nothing else, in exactly this shape:
                {
                  "discId": "<id of the chosen candidate, copied exactly>",
                  "headline": "<under 60 characters, e.g. 'A straight midrange for tight lines'>",
                  "summary": "<2-3 sentences on why this disc, for this player, right now>",
                  "reasoning": ["<short bullet>", "<short bullet>", "<short bullet>"],
                  "whenToThrowIt": "<1-2 sentences on the shots this disc is for>",
                  "plasticAdvice": "<1-2 sentences on which plastic to buy it in, given the conditions>",
                  "weightAdvice": "<1-2 sentences on what weight to buy it in and why>",
                  "confidence": <number between 0 and 1>
                }
                """;
    }

    public String userPrompt(BagAnalysis analysis, BagGap gap, PlayerProfile profile, WeatherCondition weather,
            List<RetrievedDisc> candidates) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("PLAYER\n");
        prompt.append(profile.describe()).append('\n');
        prompt.append("Skill guidance: ").append(profile.skillLevel().guidance()).append('\n');
        prompt.append("Style guidance: ").append(profile.throwingStyle().guidance()).append('\n');
        prompt.append("Course guidance: ").append(profile.courseType().guidance()).append("\n\n");

        prompt.append("CONDITIONS\n");
        prompt.append(weather.description()).append(". ").append(weather.guidance()).append("\n\n");

        prompt.append("CURRENT BAG (").append(analysis.bag().size()).append(" discs)\n");
        if (analysis.bag().isEmpty()) {
            prompt.append("- (empty)\n");
        } else {
            for (BagDisc bagDisc : analysis.bag()) {
                prompt.append("- ").append(bagDisc.label())
                        .append(" | ").append(bagDisc.flight().format())
                        .append(" | plays ").append(bagDisc.effectiveStability().label())
                        .append(" in the ").append(bagDisc.slot().label()).append(" slot");
                if (bagDisc.stabilityBreakdown().totalShift() != 0) {
                    prompt.append(" | stability: ").append(bagDisc.stabilityBreakdown().explain());
                }
                if (bagDisc.stabilityBreakdown().movedClass()) {
                    prompt.append(" | NOTE: this no longer flies as its published ")
                            .append(bagDisc.stabilityBreakdown().publishedClass().label()).append(" numbers");
                }
                prompt.append('\n');
            }
        }
        if (!analysis.notes().isEmpty()) {
            prompt.append("Bag notes: ").append(String.join(" ", analysis.notes())).append('\n');
        }
        prompt.append('\n');

        prompt.append("GAP TO FILL (computed from the bag, not by you)\n");
        prompt.append("Slot: ").append(gap.slot().label()).append(" — ").append(gap.slot().purpose()).append('\n');
        prompt.append("Missing stability: ").append(gap.stabilityClass().label())
                .append(" — ").append(gap.stabilityClass().behaviour()).append('\n');
        prompt.append("Severity score: ").append(gap.severity()).append('\n');
        prompt.append("Why: ").append(gap.reason()).append('\n');
        prompt.append("Ideal flight numbers for the gap: ").append(gap.target().format()).append('\n');
        prompt.append("Suggested weight window: ").append(gap.targetWeight().format())
                .append(" — ").append(gap.targetWeight().rationale()).append('\n');
        if (gap.nearestInBag() != null && gap.delta() != null) {
            prompt.append("Closest disc already bagged: ").append(gap.nearestInBag().label())
                    .append(" at ").append(gap.nearestInBag().flight().format())
                    .append(", flight delta ").append(gap.delta().format()).append('\n');
        }
        if (analysis.gaps().size() > 1) {
            prompt.append("Other gaps considered: ");
            prompt.append(String.join("; ", analysis.gaps().subList(1, analysis.gaps().size()).stream()
                    .map(other -> other.slot().label() + "/" + other.stabilityClass().label()).toList()));
            prompt.append('\n');
        }
        prompt.append('\n');

        prompt.append("CANDIDATE DISCS (retrieved from the catalog for this gap — choose exactly one)\n");
        for (RetrievedDisc candidate : candidates) {
            prompt.append('[').append(candidate.rank()).append("] discId=").append(candidate.discId()).append('\n');
            prompt.append("    ").append(candidate.displayName())
                    .append(" | ").append(candidate.category())
                    .append(" | ").append(candidate.flight().format())
                    .append(" | ").append(candidate.stabilityClass().label())
                    .append(" | retrieval similarity ").append(candidate.similarity()).append('\n');
            String passage = candidate.passage();
            if (passage != null && !passage.isBlank()) {
                prompt.append("    ").append(candidate.passageExcerpt(PASSAGE_CHARS)).append('\n');
            }
        }
        prompt.append("\nPick the single best candidate for this player, this gap and these conditions.");
        return prompt.toString();
    }
}
