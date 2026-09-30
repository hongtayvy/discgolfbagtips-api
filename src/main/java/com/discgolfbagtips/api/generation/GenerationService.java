package com.discgolfbagtips.api.generation;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Sub-layer 3 of the RAG pipeline: hand the retrieved discs and the player's context to a hosted
 * reasoning model and turn its answer into something the API can promise.
 *
 * <p>The model is never trusted blindly. If it names a disc that was not retrieved, the top
 * candidate is substituted and the response says so; if it is unreachable or replies with something
 * that is not the requested JSON, a deterministic template writes the answer instead. A portfolio
 * API that 502s whenever a free tier hiccups is not much of a demo.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final ChatModelClient chatModelClient;
    private final RecommendationPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public GenerationService(ChatModelClient chatModelClient, RecommendationPromptBuilder promptBuilder,
            ObjectMapper objectMapper) {
        this.chatModelClient = chatModelClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    public GeneratedRecommendation generate(BagAnalysis analysis, BagGap gap, PlayerProfile profile,
            WeatherCondition weather, List<RetrievedDisc> candidates) {

        if (candidates.isEmpty()) {
            throw new IllegalStateException("Generation was called with no retrieved candidates");
        }
        if (!chatModelClient.available()) {
            return ruleBased(gap, profile, weather, candidates,
                    "No reasoning-model API key is configured, so this explanation was written by the "
                            + "deterministic fallback rather than by a language model.", false);
        }

        long startedAt = System.nanoTime();
        try {
            String raw = chatModelClient.complete(promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(analysis, gap, profile, weather, candidates));
            return parse(raw, gap, candidates, elapsedMs(startedAt));
        } catch (ApiException ex) {
            log.warn("Falling back to the rule-based explanation: {}", ex.getMessage());
            return ruleBased(gap, profile, weather, candidates,
                    "The reasoning model was unavailable, so this explanation was written by the "
                            + "deterministic fallback. Reason: " + ex.getMessage(), true);
        }
    }

    private GeneratedRecommendation parse(String raw, BagGap gap, List<RetrievedDisc> candidates,
            long latencyMs) {
        JsonNode json;
        try {
            json = objectMapper.readTree(stripCodeFence(raw));
        } catch (JacksonException ex) {
            log.warn("Reasoning model returned unparseable JSON: {}", ex.getOriginalMessage());
            return ruleBasedFrom(gap, candidates, latencyMs,
                    "The reasoning model did not return valid JSON, so the deterministic fallback was used.");
        }

        String requestedId = text(json, "discId");
        Optional<RetrievedDisc> chosen = candidates.stream()
                .filter(candidate -> candidate.discId().equals(requestedId))
                .findFirst();

        GeneratedRecommendation.Source source = chosen.isPresent()
                ? GeneratedRecommendation.Source.LANGUAGE_MODEL
                : GeneratedRecommendation.Source.LANGUAGE_MODEL_CORRECTED;
        RetrievedDisc disc = chosen.orElseGet(candidates::getFirst);
        String note = chosen.isPresent() ? null
                : "The model named a disc outside the retrieved candidates, so the top-ranked candidate "
                        + "was substituted.";

        List<String> reasoning = new ArrayList<>();
        JsonNode reasoningNode = json.get("reasoning");
        if (reasoningNode != null && reasoningNode.isArray()) {
            reasoningNode.forEach(entry -> {
                String value = entry.asString("").trim();
                if (!value.isEmpty()) {
                    reasoning.add(value);
                }
            });
        }

        return new GeneratedRecommendation(
                disc.discId(),
                fallbackText(text(json, "headline"), "A disc to fill the biggest hole in the bag"),
                fallbackText(text(json, "summary"), disc.displayName() + " is the closest fit for this gap."),
                List.copyOf(reasoning),
                fallbackText(text(json, "whenToThrowIt"), ""),
                fallbackText(text(json, "plasticAdvice"), ""),
                fallbackText(text(json, "weightAdvice"), gap.targetWeight().rationale()),
                gap.targetWeight().format(),
                confidence(json),
                source,
                chatModelClient.model(),
                latencyMs,
                note,
                false);
    }

    /** Deterministic prose built from the same facts the model would have been given. */
    private GeneratedRecommendation ruleBased(BagGap gap, PlayerProfile profile, WeatherCondition weather,
            List<RetrievedDisc> candidates, String note, boolean transientFailure) {

        RetrievedDisc disc = candidates.getFirst();
        List<String> reasoning = new ArrayList<>();
        reasoning.add("The bag has no %s %s, which is where %s."
                .formatted(gap.stabilityClass().label(), gap.slot().label(), gap.slot().purpose()));
        reasoning.add("%s flies %s, matching the %s target for that gap."
                .formatted(disc.displayName(), disc.flight().format(), gap.target().format()));
        if (gap.nearestInBag() != null && gap.delta() != null) {
            reasoning.add("Against the closest disc already bagged (%s at %s) it is a flight delta of %s."
                    .formatted(gap.nearestInBag().label(), gap.nearestInBag().flight().format(),
                            gap.delta().format()));
        }
        reasoning.add("Buy it in %s: %s".formatted(gap.targetWeight().format(),
                gap.targetWeight().rationale()));
        reasoning.add("It stays inside what %s can throw as rated, and %s"
                .formatted(profile.skillLevel().description(),
                        weather.guidance().toLowerCase(Locale.ROOT)));

        String summary = ("%s is the closest catalog match to the %s %s this bag is missing. "
                + "It flies %s, against a target of %s for the gap.")
                .formatted(disc.displayName(), gap.stabilityClass().label(), gap.slot().label(),
                        disc.flight().format(), gap.target().format());

        String plasticAdvice = weather.gripCritical()
                ? "In %s, buy it in a grippy or gummy blend rather than a hard premium plastic."
                        .formatted(weather.description())
                : "A premium blend will hold this flight the longest; a base-grade run will season "
                        + "understable faster if you want it to.";

        return new GeneratedRecommendation(
                disc.discId(),
                "A %s %s for the gap".formatted(gap.stabilityClass().label(), gap.slot().label()),
                summary,
                List.copyOf(reasoning),
                "Reach for it on %s.".formatted(gap.slot().purpose()),
                plasticAdvice,
                "Look for %s. %s".formatted(gap.targetWeight().format(), gap.targetWeight().rationale()),
                gap.targetWeight().format(),
                0.5,
                GeneratedRecommendation.Source.RULE_BASED_FALLBACK,
                "rule-based",
                0L,
                note,
                transientFailure);
    }

    private GeneratedRecommendation ruleBasedFrom(BagGap gap, List<RetrievedDisc> candidates, long latencyMs,
            String note) {
        RetrievedDisc disc = candidates.getFirst();
        return new GeneratedRecommendation(disc.discId(),
                "A disc to fill the biggest hole in the bag",
                "%s is the closest catalog match for this gap.".formatted(disc.displayName()),
                List.of("Chosen as the top-ranked retrieval candidate."),
                "", "", gap.targetWeight().rationale(), gap.targetWeight().format(), 0.4,
                GeneratedRecommendation.Source.RULE_BASED_FALLBACK, "rule-based", latencyMs, note, true);
    }

    /** Some models wrap JSON in a fenced block despite being asked not to. */
    static String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || closingFence <= firstNewline) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, closingFence).trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString("").trim();
    }

    private static String fallbackText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double confidence(JsonNode node) {
        JsonNode value = node.get("confidence");
        if (value == null || !value.isNumber()) {
            return 0.6;
        }
        return Math.clamp(value.asDouble(), 0.0, 1.0);
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
