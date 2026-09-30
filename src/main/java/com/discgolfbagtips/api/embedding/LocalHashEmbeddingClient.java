package com.discgolfbagtips.api.embedding;

import com.discgolfbagtips.api.config.BagTipsProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * A hashing vectorizer used when no Hugging Face token is configured.
 *
 * <p>It is not a language model — it is bag-of-words hashed into the same number of dimensions with
 * sublinear term weighting — but it is deterministic, free, and lexically meaningful, so the whole
 * pipeline (including pgvector similarity ordering) can be run and demonstrated locally. Anything
 * embedded with it is tagged {@code stubbed} all the way through to the API response, so a caller
 * is never told a hosted model produced the numbers.
 */
@Component
public class LocalHashEmbeddingClient implements EmbeddingClient {

    static final String MODEL_NAME = "local-hashing-vectorizer";

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "in", "into", "is", "it",
            "its", "of", "on", "or", "that", "the", "this", "to", "with", "will", "than", "then", "so");

    private final int dimensions;

    public LocalHashEmbeddingClient(BagTipsProperties properties) {
        this.dimensions = properties.embedding().dimensions();
    }

    @Override
    public List<EmbeddingVector> embed(List<String> texts) {
        List<EmbeddingVector> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(new EmbeddingVector(EmbeddingVector.normalize(vectorize(text)), MODEL_NAME, true));
        }
        return vectors;
    }

    private float[] vectorize(String text) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+")) {
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            counts.merge(token, 1, Integer::sum);
        }
        float[] values = new float[dimensions];
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int bucket = Math.floorMod(entry.getKey().hashCode(), dimensions);
            // Sublinear weighting keeps a repeated word from dominating the vector.
            values[bucket] += (float) (1.0 + Math.log(entry.getValue()));
        }
        return values;
    }

    @Override
    public String model() {
        return MODEL_NAME;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public boolean stubbed() {
        return true;
    }
}
