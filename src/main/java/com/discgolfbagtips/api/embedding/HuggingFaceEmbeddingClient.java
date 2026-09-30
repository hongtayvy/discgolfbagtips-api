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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Hugging Face hosted inference (feature-extraction pipeline). No self-hosting and no fine-tuning:
 * a few thousand discs is nowhere near enough data to beat a good pretrained sentence encoder.
 */
@Component
public class HuggingFaceEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceEmbeddingClient.class);
    private static final ParameterizedTypeReference<List<Object>> RAW_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final BagTipsProperties.Embedding config;

    public HuggingFaceEmbeddingClient(RestClient embeddingRestClient, BagTipsProperties properties) {
        this.restClient = embeddingRestClient;
        this.config = properties.embedding();
    }

    @Override
    @CircuitBreaker(name = "embedding", fallbackMethod = "embedFallback")
    @Retry(name = "embedding")
    public List<EmbeddingVector> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<Object> response = restClient.post()
                .uri("/{model}/pipeline/feature-extraction", config.model())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("inputs", texts, "options", Map.of("wait_for_model", true)))
                .retrieve()
                .body(RAW_RESPONSE);

        if (response == null || response.size() != texts.size()) {
            throw new UpstreamServiceException("huggingface",
                    "Embedding API returned %s vectors for %d inputs"
                            .formatted(response == null ? "no" : String.valueOf(response.size()), texts.size()));
        }

        List<EmbeddingVector> vectors = new ArrayList<>(response.size());
        for (Object entry : response) {
            float[] values = EmbeddingVector.normalize(toVector(entry));
            if (values.length != config.dimensions()) {
                throw new UpstreamServiceException("huggingface",
                        "Model %s returned %d dimensions but the schema expects %d"
                                .formatted(config.model(), values.length, config.dimensions()));
            }
            vectors.add(new EmbeddingVector(values, config.model(), false));
        }
        return vectors;
    }

    @SuppressWarnings("unused")
    private List<EmbeddingVector> embedFallback(List<String> texts, Throwable cause) {
        if (cause instanceof UpstreamServiceException upstream) {
            throw upstream;
        }
        String reason = cause instanceof RestClientException ? cause.getMessage() : cause.toString();
        log.warn("Hugging Face embedding call failed for {} input(s): {}", texts.size(), reason);
        throw new UpstreamServiceException("huggingface", "Embedding model unavailable: " + reason, cause);
    }

    /**
     * The feature-extraction pipeline returns one vector per input for sentence-transformers models,
     * but token-level output (a matrix per input) is possible; mean-pool it if that happens.
     */
    private float[] toVector(Object entry) {
        if (!(entry instanceof List<?> list) || list.isEmpty()) {
            throw new UpstreamServiceException("huggingface", "Embedding API returned an unexpected payload shape");
        }
        if (list.getFirst() instanceof Number) {
            float[] values = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                values[i] = ((Number) list.get(i)).floatValue();
            }
            return values;
        }
        if (list.getFirst() instanceof List<?> firstRow) {
            float[] pooled = new float[firstRow.size()];
            for (Object rowObject : list) {
                List<?> row = (List<?>) rowObject;
                for (int i = 0; i < pooled.length; i++) {
                    pooled[i] += ((Number) row.get(i)).floatValue();
                }
            }
            for (int i = 0; i < pooled.length; i++) {
                pooled[i] /= list.size();
            }
            return pooled;
        }
        throw new UpstreamServiceException("huggingface", "Embedding API returned an unexpected payload shape");
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
}
