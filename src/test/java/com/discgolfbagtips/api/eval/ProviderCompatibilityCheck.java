package com.discgolfbagtips.api.eval;

import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.embedding.EmbeddingTextBuilder;
import com.discgolfbagtips.api.embedding.EmbeddingVector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Answers one question: can the catalog be embedded locally with Ollama and then queried through
 * Hugging Face's hosted copy of the same model?
 *
 * <p>That plan is attractive — bulk work is free and unlimited locally, while production only needs
 * to embed one short query per request — but it rests on an assumption worth testing rather than
 * believing. Two providers serving "the same" model can still disagree: GGUF quantisation, a
 * different pooling strategy (arctic-embed uses CLS pooling, not mean), or differing normalisation
 * all move the vectors. If they land in different spaces, cosine similarity between a locally
 * embedded disc and a hosted query is meaningless.
 *
 * <p>{@code DiscVectorStore} already fails safe here — it filters on {@code e.model = :model}, so
 * mixing under different identities returns nothing rather than nonsense. The risk this check
 * addresses is the opposite one: deliberately storing both under the same identity when the vectors
 * do not in fact agree.
 *
 * <pre>
 * OLLAMA_MODELS=snowflake-arctic-embed:33m \
 * HF_MODELS=Snowflake/snowflake-arctic-embed-s \
 * HUGGINGFACE_API_TOKEN=hf_... \
 *   mvn test -Peval -Dtest=ProviderCompatibilityCheck
 * </pre>
 */
@Tag("eval")
class ProviderCompatibilityCheck {

    /** Enough discs for a stable verdict without making 150 hosted calls. */
    private static final int SAMPLE_SIZE = 600;
    private static final double SAME_SPACE_THRESHOLD = 0.99;
    private static final double ACCEPTABLE_QUALITY_DROP = 0.05;

    private final EmbeddingTextBuilder textBuilder = new EmbeddingTextBuilder();

    @Test
    void localAndHostedVectorsAreInterchangeable() throws Exception {
        List<EvalProviders.Candidate> candidates = EvalProviders.available();
        EvalProviders.Candidate local = candidates.stream()
                .filter(c -> c.label().startsWith("ollama:")).findFirst().orElse(null);
        EvalProviders.Candidate hosted = candidates.stream()
                .filter(c -> c.label().startsWith("hf:")).findFirst().orElse(null);

        System.out.println();
        System.out.println("=".repeat(94));
        System.out.println("PROVIDER COMPATIBILITY CHECK");
        System.out.println("=".repeat(94));

        if (local == null || hosted == null) {
            System.out.println("""

                    Needs one Ollama model and one Hugging Face model naming the same weights, e.g.

                      OLLAMA_MODELS=snowflake-arctic-embed:33m \\
                      HF_MODELS=Snowflake/snowflake-arctic-embed-s \\
                      HUGGINGFACE_API_TOKEN=hf_... \\
                        mvn test -Peval -Dtest=ProviderCompatibilityCheck

                    Until this passes, embed and query with the SAME provider. The two are not
                    interchangeable just because the model name matches.
                    """);
            return;
        }

        System.out.printf("  local  : %s%n  hosted : %s%n%n", local.label(), hosted.label());

        List<Disc> catalog = EvalCatalog.load();
        List<Disc> sample = catalog.subList(0, Math.min(SAMPLE_SIZE, catalog.size()));
        List<EvalScenario> scenarios = EvalScenario.all();

        List<String> passages = sample.stream().map(d -> textBuilder.forDisc(d, null)).toList();
        List<String> queries = scenarios.stream()
                .map(s -> textBuilder.forGap(s.toGap(), emptyAnalysis(), s.profile(), s.weather()))
                .toList();

        System.out.printf("  embedding %d passages + %d queries with each provider...%n",
                passages.size(), queries.size());

        List<EmbeddingVector> localDocs = embed(local, passages, local.documentPrefix());
        List<EmbeddingVector> hostedDocs = embed(hosted, passages, hosted.documentPrefix());
        List<EmbeddingVector> localQueries = embed(local, queries, local.queryPrefix());
        List<EmbeddingVector> hostedQueries = embed(hosted, queries, hosted.queryPrefix());

        // 1. Do the two providers place the same text in the same position?
        double meanDoc = meanCosine(localDocs, hostedDocs);
        double minDoc = minCosine(localDocs, hostedDocs);
        double meanQuery = meanCosine(localQueries, hostedQueries);

        System.out.println();
        System.out.println("  --- vector agreement (same text, both providers) ---");
        System.out.printf("    passages : mean cosine %.5f, worst %.5f%n", meanDoc, minDoc);
        System.out.printf("    queries  : mean cosine %.5f%n", meanQuery);

        // 2. The question that actually matters: does mixing change what comes back?
        double pureLocal = 0;
        double mixed = 0;
        double overlap = 0;
        for (int i = 0; i < scenarios.size(); i++) {
            EvalScenario scenario = scenarios.get(i);
            List<Disc> rankedPure = rank(sample, localDocs, localQueries.get(i));
            List<Disc> rankedMixed = rank(sample, localDocs, hostedQueries.get(i));
            int totalRelevant = (int) sample.stream().filter(scenario.relevant()).count();
            pureLocal += RetrievalMetrics.of(rankedPure, scenario.relevant(), totalRelevant, sample.size())
                    .precisionAt5();
            mixed += RetrievalMetrics.of(rankedMixed, scenario.relevant(), totalRelevant, sample.size())
                    .precisionAt5();
            overlap += topKOverlap(rankedPure, rankedMixed);
        }
        pureLocal /= scenarios.size();
        mixed /= scenarios.size();
        overlap /= scenarios.size();

        System.out.println();
        System.out.println("  --- retrieval under mixing (local passages, hosted queries) ---");
        System.out.printf("    P@5 all-local        : %.3f%n", pureLocal);
        System.out.printf("    P@5 local docs + hosted query : %.3f%n", mixed);
        System.out.printf("    top-10 overlap       : %.1f%%%n", overlap * 100);

        boolean sameSpace = meanDoc >= SAME_SPACE_THRESHOLD && meanQuery >= SAME_SPACE_THRESHOLD;
        boolean qualityHolds = mixed >= pureLocal - ACCEPTABLE_QUALITY_DROP;

        System.out.println();
        System.out.println("  " + "-".repeat(90));
        if (sameSpace && qualityHolds) {
            System.out.println("  VERDICT: SAFE — embed the catalog locally, serve queries from the hosted model.");
        } else if (qualityHolds) {
            System.out.printf("""
                      VERDICT: MARGINAL — retrieval quality holds, but the vectors are not identical
                      (mean cosine %.5f, below the %.2f threshold). Usable, but re-embed with the
                      hosted model before you rely on the similarity scores in the explainability
                      payload meaning what they say.%n""", Math.min(meanDoc, meanQuery), SAME_SPACE_THRESHOLD);
        } else {
            System.out.printf("""
                      VERDICT: UNSAFE — do not mix. Mixing costs %.3f precision@5, which is a real
                      drop in recommendation quality. Embed and query with the same provider, and give
                      each provider its own `bagtips.embedding.model` identity so the vector store's
                      model filter keeps them apart.%n""", pureLocal - mixed);
        }
        System.out.println("  " + "-".repeat(90));
    }

    private List<EmbeddingVector> embed(EvalProviders.Candidate candidate, List<String> texts, String prefix) {
        List<String> prefixed = texts.stream().map(t -> prefix + t).toList();
        List<EmbeddingVector> all = new ArrayList<>(prefixed.size());
        for (int start = 0; start < prefixed.size(); start += candidate.batchSize()) {
            all.addAll(candidate.client()
                    .embed(prefixed.subList(start, Math.min(prefixed.size(), start + candidate.batchSize()))));
        }
        return all;
    }

    private List<Disc> rank(List<Disc> discs, List<EmbeddingVector> docs, EmbeddingVector query) {
        record Scored(Disc disc, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < discs.size(); i++) {
            scored.add(new Scored(discs.get(i), dot(docs.get(i).values(), query.values())));
        }
        return scored.stream().sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(10).map(Scored::disc).toList();
    }

    private double topKOverlap(List<Disc> a, List<Disc> b) {
        Set<String> idsA = new LinkedHashSet<>(a.stream().map(Disc::id).toList());
        long shared = b.stream().map(Disc::id).filter(idsA::contains).count();
        return a.isEmpty() ? 0 : (double) shared / a.size();
    }

    private double meanCosine(List<EmbeddingVector> a, List<EmbeddingVector> b) {
        double total = 0;
        for (int i = 0; i < a.size(); i++) {
            total += dot(a.get(i).values(), b.get(i).values());
        }
        return a.isEmpty() ? 0 : total / a.size();
    }

    private double minCosine(List<EmbeddingVector> a, List<EmbeddingVector> b) {
        double worst = 1;
        for (int i = 0; i < a.size(); i++) {
            worst = Math.min(worst, dot(a.get(i).values(), b.get(i).values()));
        }
        return worst;
    }

    /** Both sides are unit vectors, so the dot product is the cosine. */
    private static double dot(float[] a, float[] b) {
        double total = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            total += (double) a[i] * b[i];
        }
        return total;
    }

    private com.discgolfbagtips.api.analysis.BagAnalysis emptyAnalysis() {
        return new com.discgolfbagtips.api.analysis.BagAnalysis(List.of(), List.of(), List.of(),
                List.of(), Map.of(), 0, List.of(), List.of());
    }
}
