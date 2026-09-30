package com.discgolfbagtips.api.embedding;

import com.discgolfbagtips.api.common.UpstreamServiceException;
import com.discgolfbagtips.api.config.BagTipsProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Embeddings from a locally-run Ollama server.
 *
 * <p>The point is not to replace hosted inference in production but to make bulk work free: the
 * catalog can be re-embedded as often as the passage text changes without touching a rate-limited
 * quota. Vectors are stored under {@code bagtips.embedding.model} rather than Ollama's own tag, so
 * a catalog embedded locally stays queryable after switching to the hosted provider — valid only
 * when both really are the same weights.
 */
@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

    private final RestClient restClient;
    private final BagTipsProperties.Embedding config;

    public OllamaEmbeddingClient(RestClient ollamaRestClient, BagTipsProperties properties) {
        this.restClient = ollamaRestClient;
        this.config = properties.embedding();
    }

    @Override
    @CircuitBreaker(name = "embedding", fallbackMethod = "embedFallback")
    @Retry(name = "embedding")
    public List<EmbeddingVector> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        EmbedResponse response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", config.ollama().modelTag(), "input", texts))
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
            throw new UpstreamServiceException("ollama",
                    "Ollama returned %s vectors for %d inputs".formatted(
                            response == null || response.embeddings() == null
                                    ? "no" : String.valueOf(response.embeddings().size()),
                            texts.size()));
        }

        List<EmbeddingVector> vectors = new ArrayList<>(texts.size());
        for (List<Double> raw : response.embeddings()) {
            float[] values = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                values[i] = raw.get(i).floatValue();
            }
            if (values.length != config.dimensions()) {
                throw new UpstreamServiceException("ollama",
                        "Ollama model %s returned %d dimensions but the schema expects %d"
                                .formatted(config.ollama().modelTag(), values.length, config.dimensions()));
            }
            vectors.add(new EmbeddingVector(EmbeddingVector.normalize(values), config.model(), false));
        }
        return vectors;
    }

    @SuppressWarnings("unused")
    private List<EmbeddingVector> embedFallback(List<String> texts, Throwable cause) {
        if (cause instanceof UpstreamServiceException upstream) {
            throw upstream;
        }
        String reason = cause instanceof RestClientException ? cause.getMessage() : cause.toString();
        log.warn("Ollama embedding call failed for {} input(s): {}", texts.size(), reason);
        throw new UpstreamServiceException("ollama",
                "Local Ollama server unavailable (is `ollama serve` running?): " + reason, cause);
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public int dimensions() {
        return config.dimensions();
    }

    @Override
    public boolean stubbed() {
        return false;
    }

    record EmbedResponse(String model, List<List<Double>> embeddings) {
    }
}
