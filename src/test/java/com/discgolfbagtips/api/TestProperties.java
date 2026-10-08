package com.discgolfbagtips.api;

import com.discgolfbagtips.api.config.BagTipsProperties;
import java.time.Duration;
import java.util.List;

/** Property fixtures, so tests can vary one knob without restating the whole tree. */
public final class TestProperties {

    private TestProperties() {
    }

    public static BagTipsProperties defaults() {
        return withEmbedding(384, "");
    }

    public static BagTipsProperties withEmbedding(int dimensions, String apiToken) {
        return build(dimensions, apiToken, "");
    }

    public static BagTipsProperties withGenerationKey(String apiKey) {
        return build(384, "", apiKey);
    }

    private static BagTipsProperties build(int dimensions, String embeddingToken, String generationKey) {
        return new BagTipsProperties(
                new BagTipsProperties.DiscIt("https://discit-api.fly.dev", Duration.ofSeconds(10),
                        Duration.ofSeconds(60), true),
                new BagTipsProperties.Embedding("https://example.invalid", "test-embedding-model", dimensions,
                        embeddingToken, 32, Duration.ofSeconds(10), Duration.ofSeconds(60), true, 500,
                        Duration.ofMillis(250), "auto",
                        new BagTipsProperties.Ollama("http://localhost:11434", "all-minilm",
                                Duration.ofSeconds(10), Duration.ofSeconds(120)),
                        "", ""),
                new BagTipsProperties.Retrieval(8, 40, 0.15, 3),
                new BagTipsProperties.Generation("groq", "https://example.invalid", "test-reasoning-model",
                        generationKey, 0.3, 900, Duration.ofSeconds(10), Duration.ofSeconds(60)),
                new BagTipsProperties.RateLimit(true, 60, 60, Duration.ofMinutes(1), 10, 10, Duration.ofMinutes(1), 1),
                new BagTipsProperties.Cors(List.of("http://localhost:5173")),
                new BagTipsProperties.Cache(true, Duration.ofHours(24), 5000),
                new BagTipsProperties.Auth("", "authenticated"),
                "test-admin-token");
    }
}
