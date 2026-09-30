package com.discgolfbagtips.api.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.discgolfbagtips.api.TestProperties;
import com.discgolfbagtips.api.config.BagTipsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalHashEmbeddingClientTest {

    private final LocalHashEmbeddingClient client = new LocalHashEmbeddingClient(properties(64));

    @Test
    void producesDeterministicUnitVectors() {
        EmbeddingVector first = client.embed(List.of("overstable fairway driver")).getFirst();
        EmbeddingVector second = client.embed(List.of("overstable fairway driver")).getFirst();

        assertThat(first.values()).containsExactly(second.values());
        assertThat(magnitude(first)).isCloseTo(1.0, within(1e-5));
        assertThat(first.dimensions()).isEqualTo(64);
        assertThat(first.stubbed()).isTrue();
    }

    @Test
    void relatedTextsSitCloserThanUnrelatedOnes() {
        EmbeddingVector query = client.embed(List.of("understable midrange for hyzer flips in the woods"))
                .getFirst();
        EmbeddingVector related = client.embed(List.of("a midrange that hyzer flips, understable, wooded lines"))
                .getFirst();
        EmbeddingVector unrelated = client.embed(List.of("very overstable distance driver for headwind bombs"))
                .getFirst();

        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
    }

    @Test
    void emitsPgvectorLiterals() {
        EmbeddingVector vector = new LocalHashEmbeddingClient(properties(3))
                .embed(List.of("straight stable putter")).getFirst();

        assertThat(vector.toVectorLiteral()).startsWith("[").endsWith("]");
        assertThat(vector.toVectorLiteral().split(",")).hasSize(3);
    }

    private double magnitude(EmbeddingVector vector) {
        double sum = 0;
        for (float value : vector.values()) {
            sum += (double) value * value;
        }
        return Math.sqrt(sum);
    }

    private double cosine(EmbeddingVector left, EmbeddingVector right) {
        double dot = 0;
        for (int i = 0; i < left.values().length; i++) {
            dot += (double) left.values()[i] * right.values()[i];
        }
        return dot;
    }

    private BagTipsProperties properties(int dimensions) {
        return TestProperties.withEmbedding(dimensions, "");
    }
}
