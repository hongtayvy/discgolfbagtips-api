package com.discgolfbagtips.api.embedding;

import java.util.List;

/** Pluggable embedding backend, so the hosted model can be swapped without touching retrieval. */
public interface EmbeddingClient {

    List<EmbeddingVector> embed(List<String> texts);

    String model();

    int dimensions();

    /** True when the vectors are produced locally rather than by the hosted model. */
    boolean stubbed();
}
