package com.discgolfbagtips.api.eval;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.embedding.EmbeddingClient;
import com.discgolfbagtips.api.embedding.EmbeddingTextBuilder;
import com.discgolfbagtips.api.embedding.EmbeddingVector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Compares embedding models on this catalog and these questions, rather than on a general-purpose
 * leaderboard.
 *
 * <p>MTEB scores rank models on web and Wikipedia retrieval. "An understable midrange to hyzer-flip
 * on tight wooded lines" is not that distribution, and the passages being indexed are generated
 * prose about flight characteristics. The only way to know which model reads them best is to measure
 * it here.
 *
 * <p>Runs entirely in memory — catalog from DiscIt (cached), cosine similarity over a list, no
 * database. Excluded from the normal build; run it explicitly:
 *
 * <pre>
 * # local models, free and unlimited (needs: brew install ollama && ollama pull all-minilm)
 * OLLAMA_MODELS=all-minilm,snowflake-arctic-embed:33m ./mvnw test -Peval -Dtest=EmbeddingModelEvaluation
 *
 * # hosted models
 * HUGGINGFACE_API_TOKEN=hf_... HF_MODELS=BAAI/bge-small-en-v1.5,thenlper/gte-small \
 *   ./mvnw test -Peval -Dtest=EmbeddingModelEvaluation
 * </pre>
 */
@Tag("eval")
class EmbeddingModelEvaluation {

    private static final int RANK_DEPTH = 10;

    private final EmbeddingTextBuilder textBuilder = new EmbeddingTextBuilder();

    @Test
    void compareModels() throws Exception {
        List<Disc> catalog = EvalCatalog.load();
        List<EvalScenario> scenarios = EvalScenario.all();
        List<EvalProviders.Candidate> candidates = EvalProviders.available();

        System.out.println();
        System.out.println("=".repeat(100));
        System.out.printf("EMBEDDING MODEL EVALUATION — %d discs, %d scenarios%n", catalog.size(), scenarios.size());
        System.out.println("=".repeat(100));

        if (candidates.isEmpty()) {
            System.out.println("""

                    No embedding providers configured, so only the flight-number baseline will run.
                      Local  : brew install ollama && ollama pull all-minilm
                               then OLLAMA_MODELS=all-minilm
                      Hosted : HUGGINGFACE_API_TOKEN=hf_... HF_MODELS=BAAI/bge-small-en-v1.5
                    """);
        }

        List<Result> results = new ArrayList<>();
        results.add(evaluateFlightNumberBaseline(catalog, scenarios));

        for (EvalProviders.Candidate candidate : candidates) {
            try {
                results.add(evaluateEmbeddingModel(candidate, catalog, scenarios));
            } catch (RuntimeException ex) {
                System.out.printf("%n  !! %s failed: %s%n", candidate.label(), ex.getMessage());
            }
        }

        printSummary(results);
        printPerScenario(results, scenarios);
    }

    private Result evaluateEmbeddingModel(EvalProviders.Candidate candidate, List<Disc> catalog,
            List<EvalScenario> scenarios) {

        System.out.printf("%n--- %s ---%n", candidate.label());
        EmbeddingClient client = candidate.client();

        long embedStart = System.nanoTime();
        List<String> passages = catalog.stream()
                .map(disc -> candidate.documentPrefix() + textBuilder.forDisc(disc, null))
                .toList();
        List<EmbeddingVector> docVectors = embedInBatches(client, passages, candidate.batchSize());
        long embedMs = (System.nanoTime() - embedStart) / 1_000_000;
        System.out.printf("    embedded %d passages in %,d ms (%.1f/s), %d dims%n",
                docVectors.size(), embedMs, docVectors.size() / (embedMs / 1000.0 + 0.001),
                docVectors.isEmpty() ? 0 : docVectors.getFirst().dimensions());

        Map<String, RetrievalMetrics> perScenario = new java.util.LinkedHashMap<>();
        for (EvalScenario scenario : scenarios) {
            String queryText = candidate.queryPrefix()
                    + textBuilder.forGap(scenario.toGap(), emptyAnalysis(), scenario.profile(), scenario.weather());
            EmbeddingVector queryVector = client.embed(List.of(queryText)).getFirst();
            List<Disc> ranked = rankByCosine(catalog, docVectors, queryVector);
            int totalRelevant = (int) catalog.stream().filter(scenario.relevant()).count();
            perScenario.put(scenario.id(),
                    RetrievalMetrics.of(ranked, scenario.relevant(), totalRelevant, catalog.size()));
        }
        return new Result(candidate.label(), perScenario, embedMs);
    }

    /** The honest control: does semantic search actually beat ranking by flight-number distance? */
    private Result evaluateFlightNumberBaseline(List<Disc> catalog, List<EvalScenario> scenarios) {
        Map<String, RetrievalMetrics> perScenario = new java.util.LinkedHashMap<>();
        for (EvalScenario scenario : scenarios) {
            var target = scenario.target();
            List<Disc> ranked = catalog.stream()
                    .sorted(Comparator.comparingDouble(disc -> {
                        double ds = disc.speed() - target.speed();
                        double dg = disc.glide() - target.glide();
                        double dt = disc.turn() - target.turn();
                        double df = disc.fade() - target.fade();
                        return ds * ds + dg * dg + dt * dt + df * df;
                    }))
                    .limit(RANK_DEPTH)
                    .toList();
            int totalRelevant = (int) catalog.stream().filter(scenario.relevant()).count();
            perScenario.put(scenario.id(),
                    RetrievalMetrics.of(ranked, scenario.relevant(), totalRelevant, catalog.size()));
        }
        return new Result("flight-numbers (baseline)", perScenario, 0);
    }

    private List<Disc> rankByCosine(List<Disc> catalog, List<EmbeddingVector> docVectors, EmbeddingVector query) {
        record Scored(Disc disc, double score) {
        }
        List<Scored> scored = new ArrayList<>(catalog.size());
        for (int i = 0; i < catalog.size(); i++) {
            scored.add(new Scored(catalog.get(i), cosine(docVectors.get(i).values(), query.values())));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(RANK_DEPTH)
                .map(Scored::disc)
                .toList();
    }

    /** Both sides are unit-normalised by the clients, so the dot product is the cosine. */
    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }

    private List<EmbeddingVector> embedInBatches(EmbeddingClient client, List<String> texts, int batchSize) {
        List<EmbeddingVector> all = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(texts.size(), start + batchSize);
            all.addAll(client.embed(texts.subList(start, end)));
            if (start > 0 && start % (batchSize * 10) == 0) {
                System.out.printf("    ... %d/%d%n", start, texts.size());
            }
        }
        return all;
    }

    private BagAnalysis emptyAnalysis() {
        return new BagAnalysis(List.of(), List.of(), List.of(), List.of(), Map.of(), 0, List.of(), List.of());
    }

    private void printSummary(List<Result> results) {
        System.out.println();
        System.out.println("=".repeat(100));
        System.out.println("SUMMARY — averaged over all scenarios");
        System.out.println("=".repeat(100));
        System.out.printf("%-34s %8s %8s %8s %8s %8s %10s%n",
                "model", "P@5", "P@10", "R@10", "MRR", "lift@5", "embed ms");
        System.out.println("-".repeat(100));
        results.stream()
                .sorted(Comparator.comparingDouble((Result r) -> r.mean().precisionAt5()).reversed())
                .forEach(r -> {
                    RetrievalMetrics m = r.mean();
                    System.out.printf("%-34s %8.3f %8.3f %8.3f %8.3f %7.1fx %10s%n",
                            r.label(), m.precisionAt5(), m.precisionAt10(), m.recallAt10(),
                            m.meanReciprocalRank(), m.liftAt5(),
                            r.embedMs() == 0 ? "-" : String.format("%,d", r.embedMs()));
                });
        System.out.println();
        System.out.println("lift@5 = precision@5 divided by the base rate. 1.0x is a coin flip.");
    }

    private void printPerScenario(List<Result> results, List<EvalScenario> scenarios) {
        System.out.println();
        System.out.println("=".repeat(100));
        System.out.println("PRECISION@5 BY SCENARIO");
        System.out.println("=".repeat(100));
        System.out.printf("%-26s %9s", "scenario", "base");
        results.forEach(r -> System.out.printf(" %22s", truncate(r.label(), 22)));
        System.out.println();
        System.out.println("-".repeat(100));
        for (EvalScenario scenario : scenarios) {
            RetrievalMetrics any = results.getFirst().perScenario().get(scenario.id());
            System.out.printf("%-26s %9.3f", truncate(scenario.id(), 26), any.baseRate());
            for (Result r : results) {
                System.out.printf(" %22.3f", r.perScenario().get(scenario.id()).precisionAt5());
            }
            System.out.println();
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private record Result(String label, Map<String, RetrievalMetrics> perScenario, long embedMs) {

        RetrievalMetrics mean() {
            return RetrievalMetrics.mean(List.copyOf(perScenario.values()));
        }
    }
}
