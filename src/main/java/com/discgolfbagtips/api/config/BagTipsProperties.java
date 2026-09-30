package com.discgolfbagtips.api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every tunable knob for the RAG pipeline lives here so the three service sub-layers
 * (embedding, retrieval, generation) can be re-pointed at different providers without code changes.
 */
@ConfigurationProperties(prefix = "bagtips")
public record BagTipsProperties(
        @DefaultValue DiscIt discit,
        @DefaultValue Embedding embedding,
        @DefaultValue Retrieval retrieval,
        @DefaultValue Generation generation,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Cors cors,
        @DefaultValue Cache cache,
        @DefaultValue("") String adminToken) {

    /** Upstream open-source disc catalog. Synced on a schedule; never called during a user request. */
    public record DiscIt(
            @DefaultValue("https://discit-api.fly.dev") String baseUrl,
            @DefaultValue("10s") Duration connectTimeout,
            @DefaultValue("60s") Duration readTimeout,
            @DefaultValue("true") boolean syncEnabled) {
    }

    /**
     * Hugging Face hosted inference API. When {@code apiToken} is blank the app falls back to a
     * deterministic local embedder so the service still boots and demos end-to-end.
     */
    public record Embedding(
            @DefaultValue("https://router.huggingface.co/hf-inference/models") String baseUrl,
            @DefaultValue("sentence-transformers/all-MiniLM-L6-v2") String model,
            @DefaultValue("384") int dimensions,
            @DefaultValue("") String apiToken,
            @DefaultValue("32") int batchSize,
            @DefaultValue("10s") Duration connectTimeout,
            @DefaultValue("60s") Duration readTimeout,
            @DefaultValue("true") boolean backfillEnabled,
            /* Ceiling for one scheduled tick. The full backfill ignores this and runs to completion. */
            @DefaultValue("500") int maxPerRun,
            /* Pause between batches during a full backfill, to stay friendly to a free-tier quota. */
            @DefaultValue("250ms") Duration backfillThrottle,
            @DefaultValue("auto") String provider,
            @DefaultValue Ollama ollama,
            /*
             * Asymmetric retrieval models want their own markers on each side (E5 wants "query: " /
             * "passage: "; BGE wants a query instruction and no document prefix). Getting these wrong
             * silently costs most of the quality that motivated switching model in the first place.
             */
            @DefaultValue("") String queryPrefix,
            @DefaultValue("") String documentPrefix) {

        public boolean hasCredentials() {
            return apiToken != null && !apiToken.isBlank();
        }

        /** Which backend to use: {@code auto} picks Hugging Face when a token exists, else local. */
        public Provider resolvedProvider() {
            return switch (provider == null ? "auto" : provider.toLowerCase(java.util.Locale.ROOT)) {
                case "huggingface", "hf" -> Provider.HUGGING_FACE;
                case "ollama" -> Provider.OLLAMA;
                case "local", "hash" -> Provider.LOCAL_HASH;
                default -> hasCredentials() ? Provider.HUGGING_FACE : Provider.LOCAL_HASH;
            };
        }

        public enum Provider {
            HUGGING_FACE,
            OLLAMA,
            LOCAL_HASH
        }
    }

    /**
     * A locally-run Ollama server. Useful for bulk-embedding the catalog and for iterating on the
     * passage text without spending hosted quota.
     *
     * <p>{@code modelTag} is how Ollama names the model; the vectors are still stored under
     * {@code bagtips.embedding.model}, because that is the identity retrieval matches on. Only point
     * these two at genuinely the same weights — see the compatibility check in the eval harness.
     */
    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("all-minilm") String modelTag,
            @DefaultValue("10s") Duration connectTimeout,
            @DefaultValue("120s") Duration readTimeout) {
    }

    /** pgvector similarity search tuning. */
    public record Retrieval(
            @DefaultValue("8") int candidateLimit,
            @DefaultValue("40") int overFetch,
            @DefaultValue("0.15") double minimumSimilarity,
            @DefaultValue("3") int alternativeCount) {
    }

    /**
     * Free-tier hosted reasoning model. Groq and OpenRouter both speak the OpenAI
     * chat-completions dialect, so one client covers either.
     */
    public record Generation(
            @DefaultValue("groq") String provider,
            @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
            @DefaultValue("llama-3.3-70b-versatile") String model,
            @DefaultValue("") String apiKey,
            @DefaultValue("0.3") double temperature,
            @DefaultValue("900") int maxTokens,
            @DefaultValue("10s") Duration connectTimeout,
            @DefaultValue("60s") Duration readTimeout) {

        public boolean hasCredentials() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** Per-client token bucket applied by {@link RateLimitFilter}. */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("60") int capacity,
            @DefaultValue("60") int refillTokens,
            @DefaultValue("1m") Duration refillPeriod,
            @DefaultValue("10") int recommendationCapacity,
            @DefaultValue("10") int recommendationRefillTokens,
            @DefaultValue("1m") Duration recommendationRefillPeriod) {
    }

    /**
     * In-process analysis cache. Twenty-four hours because the inputs are a bag and a weather
     * category: neither changes minute to minute, and a player revisiting the same bag the next
     * morning should still get an instant answer.
     */
    public record Cache(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("24h") Duration ttl,
            @DefaultValue("5000") int maxEntries) {
    }

    public record Cors(
            @DefaultValue({"http://localhost:5173", "http://localhost:3000"}) List<String> allowedOrigins) {
    }
}
