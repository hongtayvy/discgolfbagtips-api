package com.discgolfbagtips.api.embedding;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.config.BagTipsProperties;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.WeatherCondition;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sub-layer 1 of the RAG pipeline: turn structured input into a passage, then into a vector.
 *
 * <p>Which backend does the work is decided once, at startup, from whether a Hugging Face token is
 * present. Everything downstream reads {@link #stubbed()} rather than re-deriving that.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingClient client;
    private final EmbeddingTextBuilder textBuilder;
    private final int batchSize;
    private final String queryPrefix;
    private final String documentPrefix;
    private final String providerLabel;

    public EmbeddingService(HuggingFaceEmbeddingClient huggingFaceClient, OllamaEmbeddingClient ollamaClient,
            LocalHashEmbeddingClient localClient, EmbeddingTextBuilder textBuilder,
            BagTipsProperties properties) {
        BagTipsProperties.Embedding config = properties.embedding();
        this.textBuilder = textBuilder;
        this.batchSize = Math.max(1, config.batchSize());
        this.queryPrefix = config.queryPrefix() == null ? "" : config.queryPrefix();
        this.documentPrefix = config.documentPrefix() == null ? "" : config.documentPrefix();

        switch (config.resolvedProvider()) {
            case HUGGING_FACE -> {
                this.client = huggingFaceClient;
                this.providerLabel = "huggingface-inference-api";
                log.info("Embeddings: Hugging Face hosted inference, model {}", config.model());
            }
            case OLLAMA -> {
                this.client = ollamaClient;
                this.providerLabel = "ollama";
                log.info("Embeddings: local Ollama at {}, tag {}, stored as model {}",
                        config.ollama().baseUrl(), config.ollama().modelTag(), config.model());
            }
            case LOCAL_HASH -> {
                this.client = localClient;
                this.providerLabel = "local";
                log.warn("Embeddings: no hosted provider configured — falling back to the local hashing "
                        + "vectorizer. Responses will be marked as stubbed.");
            }
            default -> throw new IllegalStateException("Unhandled embedding provider");
        }
    }

    /** Embeds the passage describing a disc; this is what gets stored in pgvector. */
    public DiscEmbedding embedDisc(Disc disc, PlasticType plastic) {
        String text = textBuilder.forDisc(disc, plastic);
        EmbeddingVector vector = embedOne(documentPrefix + text);
        return new DiscEmbedding(disc.id(), text, vector, textBuilder.descriptors(disc, plastic));
    }

    public List<DiscEmbedding> embedDiscs(List<Disc> discs, PlasticType plastic) {
        List<String> texts = discs.stream().map(disc -> textBuilder.forDisc(disc, plastic)).toList();
        List<EmbeddingVector> vectors =
                embedBatched(texts.stream().map(text -> documentPrefix + text).toList());
        List<DiscEmbedding> embeddings = new ArrayList<>(discs.size());
        for (int i = 0; i < discs.size(); i++) {
            embeddings.add(new DiscEmbedding(discs.get(i).id(), texts.get(i), vectors.get(i),
                    textBuilder.descriptors(discs.get(i), plastic)));
        }
        return embeddings;
    }

    /** Embeds the request side: the gap, the player and the day, written the same way as a disc. */
    public QueryEmbedding embedGap(BagGap gap, BagAnalysis analysis, PlayerProfile profile,
            WeatherCondition weather) {
        String text = textBuilder.forGap(gap, analysis, profile, weather);
        return new QueryEmbedding(text, embedOne(queryPrefix + text));
    }

    public List<EmbeddingVector> embedBatched(List<String> texts) {
        List<EmbeddingVector> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            vectors.addAll(client.embed(texts.subList(start, Math.min(texts.size(), start + batchSize))));
        }
        return vectors;
    }

    public String model() {
        return client.model();
    }

    public int dimensions() {
        return client.dimensions();
    }

    public boolean stubbed() {
        return client.stubbed();
    }

    public String provider() {
        return providerLabel;
    }

    private EmbeddingVector embedOne(String text) {
        List<EmbeddingVector> vectors = client.embed(List.of(text));
        if (vectors.isEmpty()) {
            throw new IllegalStateException("Embedding client returned no vector for a single input");
        }
        return vectors.getFirst();
    }

    /** A disc's passage, its vector and the tags that explain what the passage says. */
    public record DiscEmbedding(String discId, String text, EmbeddingVector vector, List<String> descriptors) {
    }

    public record QueryEmbedding(String text, EmbeddingVector vector) {
    }
}
