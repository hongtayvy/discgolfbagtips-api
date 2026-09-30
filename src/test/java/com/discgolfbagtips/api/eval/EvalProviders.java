package com.discgolfbagtips.api.eval;

import com.discgolfbagtips.api.embedding.EmbeddingClient;
import com.discgolfbagtips.api.embedding.EmbeddingVector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Discovers which embedding backends this machine can actually reach, so the harness runs with
 * whatever is available and quietly skips the rest.
 *
 * <p>Model prefixes are applied per candidate rather than globally: E5 and Nomic are asymmetric and
 * lose most of their advantage without them, while GTE wants none. Comparing a prefixed model
 * against an unprefixed one would measure the prefix, not the model.
 */
final class EvalProviders {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private EvalProviders() {
    }

    record Candidate(String label, EmbeddingClient client, String queryPrefix, String documentPrefix,
            int batchSize) {
    }

    static List<Candidate> available() {
        List<Candidate> candidates = new ArrayList<>();
        // Always present: needs no credentials, and separates "the model understands the passage"
        // from "the words simply overlap". A hosted model that cannot beat bag-of-words is not
        // earning its latency.
        candidates.add(new Candidate("local hashing (lexical)",
                new com.discgolfbagtips.api.embedding.LocalHashEmbeddingClient(
                        com.discgolfbagtips.api.TestProperties.withEmbedding(384, "")),
                "", "", 256));
        candidates.addAll(ollamaCandidates());
        candidates.addAll(huggingFaceCandidates());
        return candidates;
    }

    // --- Ollama -------------------------------------------------------------

    private static List<Candidate> ollamaCandidates() {
        String models = env("OLLAMA_MODELS");
        if (models.isBlank()) {
            return List.of();
        }
        String baseUrl = envOrDefault("OLLAMA_URL", "http://localhost:11434");
        if (!reachable(baseUrl + "/api/version")) {
            System.out.printf("  (skipping Ollama — nothing answering at %s; is `ollama serve` running?)%n",
                    baseUrl);
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (String tag : models.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                candidates.add(new Candidate("ollama:" + trimmed, new OllamaEvalClient(baseUrl, trimmed),
                        queryPrefixFor(trimmed), documentPrefixFor(trimmed), 32));
            }
        }
        return candidates;
    }

    // --- Hugging Face -------------------------------------------------------

    private static List<Candidate> huggingFaceCandidates() {
        String token = env("HUGGINGFACE_API_TOKEN");
        String models = env("HF_MODELS");
        if (token.isBlank() || models.isBlank()) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (String model : models.split(",")) {
            String trimmed = model.trim();
            if (!trimmed.isEmpty()) {
                candidates.add(new Candidate("hf:" + trimmed, new HuggingFaceEvalClient(token, trimmed),
                        queryPrefixFor(trimmed), documentPrefixFor(trimmed), 16));
            }
        }
        return candidates;
    }

    /**
     * Asymmetric models expect a marker telling them which side of the retrieval pair a text is.
     * Getting this wrong is the single most common reason a "better" model benchmarks worse.
     */
    static String queryPrefixFor(String model) {
        String m = model.toLowerCase(Locale.ROOT);
        if (m.contains("e5")) {
            return "query: ";
        }
        if (m.contains("nomic")) {
            return "search_query: ";
        }
        if (m.contains("bge") || m.contains("arctic")) {
            return "Represent this sentence for searching relevant passages: ";
        }
        return "";
    }

    static String documentPrefixFor(String model) {
        String m = model.toLowerCase(Locale.ROOT);
        if (m.contains("e5")) {
            return "passage: ";
        }
        if (m.contains("nomic")) {
            return "search_document: ";
        }
        return "";
    }

    private static boolean reachable(String url) {
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build()
                    .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static String envOrDefault(String name, String fallback) {
        String value = env(name);
        return value.isBlank() ? fallback : value;
    }

    /** Minimal Ollama client — the harness must not depend on Spring wiring. */
    private record OllamaEvalClient(String baseUrl, String tag) implements EmbeddingClient {

        @Override
        public List<EmbeddingVector> embed(List<String> texts) {
            String body = MAPPER.writeValueAsString(Map.of("model", tag, "input", texts));
            String json = post(baseUrl + "/api/embed", body, null);
            var node = MAPPER.readTree(json).get("embeddings");
            if (node == null || !node.isArray()) {
                throw new IllegalStateException("Ollama returned no embeddings for tag " + tag);
            }
            List<EmbeddingVector> vectors = new ArrayList<>();
            node.forEach(row -> {
                float[] values = new float[row.size()];
                for (int i = 0; i < row.size(); i++) {
                    values[i] = (float) row.get(i).asDouble();
                }
                vectors.add(new EmbeddingVector(EmbeddingVector.normalize(values), tag, false));
            });
            return vectors;
        }

        @Override
        public String model() {
            return tag;
        }

        @Override
        public int dimensions() {
            return 0;
        }

        @Override
        public boolean stubbed() {
            return false;
        }
    }

    private record HuggingFaceEvalClient(String token, String model) implements EmbeddingClient {

        @Override
        public List<EmbeddingVector> embed(List<String> texts) {
            String body = MAPPER.writeValueAsString(
                    Map.of("inputs", texts, "options", Map.of("wait_for_model", true)));
            String json = post(
                    "https://router.huggingface.co/hf-inference/models/" + model + "/pipeline/feature-extraction",
                    body, token);
            var root = MAPPER.readTree(json);
            List<EmbeddingVector> vectors = new ArrayList<>();
            root.forEach(row -> {
                float[] values = new float[row.size()];
                for (int i = 0; i < row.size(); i++) {
                    values[i] = (float) row.get(i).asDouble();
                }
                vectors.add(new EmbeddingVector(EmbeddingVector.normalize(values), model, false));
            });
            return vectors;
        }

        @Override
        public String model() {
            return model;
        }

        @Override
        public int dimensions() {
            return 0;
        }

        @Override
        public boolean stubbed() {
            return false;
        }
    }

    private static String post(String url, String body, String bearerToken) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (bearerToken != null && !bearerToken.isBlank()) {
                request.header("Authorization", "Bearer " + bearerToken);
            }
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15)).build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP %d: %s".formatted(response.statusCode(),
                        response.body().length() > 300 ? response.body().substring(0, 300) : response.body()));
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while embedding", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }
}
