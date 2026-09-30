package com.discgolfbagtips.api.generation;

import java.util.List;

/**
 * The reasoning model's answer, after it has been validated against the retrieved candidates.
 *
 * @param discId     always one of the retrieved candidates — a model that names something else is overruled
 * @param source     which path produced this text
 */
public record GeneratedRecommendation(
        String discId,
        String headline,
        String summary,
        List<String> reasoning,
        String whenToThrowIt,
        String plasticAdvice,
        String weightAdvice,
        String suggestedWeightRange,
        double confidence,
        Source source,
        String model,
        long latencyMs,
        String note,
        /* The model was configured but failed, as opposed to never having been configured. */
        boolean transientFailure) {

    public enum Source {
        /** Written by the hosted reasoning model. */
        LANGUAGE_MODEL,
        /** The model named a disc that was not retrieved, so the top candidate was substituted. */
        LANGUAGE_MODEL_CORRECTED,
        /** No model was reachable (or configured); the deterministic template wrote the answer. */
        RULE_BASED_FALLBACK
    }

    public boolean fromModel() {
        return source != Source.RULE_BASED_FALLBACK;
    }
}
