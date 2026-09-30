package com.discgolfbagtips.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.TestProperties;
import com.discgolfbagtips.api.analysis.BagAnalyzer;
import com.discgolfbagtips.api.analysis.RedundancyAnalyzer;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.catalog.WearState;
import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.generation.ChatModelClient;
import com.discgolfbagtips.api.generation.GeneratedRecommendation;
import com.discgolfbagtips.api.generation.GenerationService;
import com.discgolfbagtips.api.player.CourseConditions;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.BagDiscRequest;
import com.discgolfbagtips.api.recommendation.dto.RecommendationResponse;
import com.discgolfbagtips.api.retrieval.RetrievalResult;
import com.discgolfbagtips.api.retrieval.RetrievalService;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {

    private static final BagAnalysisRequest REQUEST = new BagAnalysisRequest(
            List.of(new BagDiscRequest("buzzz", null, null, "ESP", 177, WearState.BEAT_IN)),
            new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.FOREHAND, CourseType.WOODED),
            new CourseConditions(WeatherCondition.WINDY), null, null);

    private final BagResolver bagResolver = mock(BagResolver.class);
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final GenerationService generationService = mock(GenerationService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final ChatModelClient chatModelClient = mock(ChatModelClient.class);

    private RecommendationService service;

    private final RetrievedDisc zone = candidate("zone", "Discraft", "Zone", 4, 3, 0, 3, 0.83, 1);
    private final RetrievedDisc harp = candidate("harp", "Westside", "Harp", 5, 4, 0, 4, 0.79, 2);

    @BeforeEach
    void setUp() {
        service = new RecommendationService(bagResolver, new BagAnalyzer(new RedundancyAnalyzer()), retrievalService, generationService,
                new ExplainabilityAssembler(embeddingService), chatModelClient, TestProperties.defaults());

        when(bagResolver.resolve(anyList())).thenReturn(new BagResolver.ResolvedBag(
                List.of(new BagDisc(TestDiscs.disc("buzzz", "Discraft", "Buzzz", "Midrange", 5, 4, -1, 1),
                        null, "ESP", 177, WearState.BEAT_IN)),
                List.of("Typo Disc")));
        when(embeddingService.provider()).thenReturn("huggingface-inference-api");
        when(embeddingService.dimensions()).thenReturn(384);
        when(chatModelClient.provider()).thenReturn("groq");
    }

    private void givenRetrieval(RetrievalResult result) {
        when(retrievalService.retrieve(any(), any(), any(), any(), any())).thenReturn(result);
    }

    private void givenGeneration(GeneratedRecommendation generated) {
        when(generationService.generate(any(), any(), any(), any(), anyList())).thenReturn(generated);
    }

    @Test
    void assemblesTheRecommendationWithItsFullAuditTrail() {
        givenRetrieval(vectorResult(List.of(zone, harp)));
        givenGeneration(new GeneratedRecommendation("harp", "An overstable approach",
                "Fills the overstable hole.", List.of("because"), "Forehand approaches.", "VIP.",
                "Max weight.", "170-174 g", 0.8, GeneratedRecommendation.Source.LANGUAGE_MODEL, "test-reasoning-model", 700, null, false));

        RecommendationResponse response = service.recommend(REQUEST, "session-1");

        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.recommendation().discId()).isEqualTo("harp");
        assertThat(response.recommendation().name()).isEqualTo("Harp");
        assertThat(response.alternatives()).extracting("discId").containsExactly("zone");

        var explainability = response.explainability();
        assertThat(explainability.retrievalMode()).isEqualTo("VECTOR_SIMILARITY");
        assertThat(explainability.vectorMatches()).hasSize(2);
        assertThat(explainability.vectorMatches())
                .filteredOn(match -> match.chosen())
                .extracting("discId").containsExactly("harp");
        assertThat(explainability.flightGaps()).isNotEmpty();
        assertThat(explainability.flightGaps()).filteredOn(gap -> gap.selected()).hasSize(1);
        assertThat(explainability.citations()).anyMatch(citation -> citation.startsWith("flight-gap:"));
        assertThat(explainability.citations()).anyMatch(citation -> citation.startsWith("vector-match#1:"));
        assertThat(explainability.embedding().model()).isEqualTo("test-embedding-model");
        assertThat(explainability.generation().model()).isEqualTo("test-reasoning-model");
        assertThat(explainability.degradations()).isEmpty();
    }

    @Test
    void everyVectorMatchCarriesItsDeltaAgainstTheGapTarget() {
        givenRetrieval(vectorResult(List.of(zone, harp)));
        givenGeneration(chose("zone"));

        var matches = service.recommend(REQUEST, "session-1").explainability().vectorMatches();

        assertThat(matches).allSatisfy(match -> {
            assertThat(match.deltaVsGapTarget()).isNotNull();
            assertThat(match.similarity()).isPositive();
            assertThat(match.passageExcerpt()).isNotBlank();
        });
    }

    @Test
    void degradedPathsAreReportedRatherThanHidden() {
        givenRetrieval(new RetrievalResult(RetrievalResult.RetrievalMode.FLIGHT_NUMBER_FALLBACK,
                "Flight-number nearest neighbour", "flight-numbers", false, List.of(zone), 4,
                "Embedding model unavailable", "none"));
        givenGeneration(new GeneratedRecommendation("zone", "headline", "summary", List.of(), "", "", "", "170-174 g",
                0.5, GeneratedRecommendation.Source.RULE_BASED_FALLBACK, "rule-based", 0,
                "The reasoning model was unavailable", true));

        var explainability = service.recommend(REQUEST, "session-1").explainability();

        assertThat(explainability.retrievalMode()).isEqualTo("FLIGHT_NUMBER_FALLBACK");
        assertThat(explainability.degradations())
                .anyMatch(note -> note.startsWith("retrieval:"))
                .anyMatch(note -> note.startsWith("generation:"));
        assertThat(explainability.generation().stubbed()).isTrue();
    }

    @Test
    void unmatchedBagEntriesAreEchoedBackRatherThanFailingTheRequest() {
        givenRetrieval(vectorResult(List.of(zone)));
        givenGeneration(chose("zone"));

        var analysis = service.recommend(REQUEST, "session-1").analysis();

        assertThat(analysis.unresolvedEntries()).containsExactly("Typo Disc");
        assertThat(analysis.discCount()).isEqualTo(1);
        assertThat(analysis.resolvedBag()).extracting("name").containsExactly("Buzzz");
    }

    @Test
    void theAnswerSaysWhatWeightToBuyNotJustWhichMold() {
        givenRetrieval(vectorResult(List.of(zone)));
        givenGeneration(chose("zone"));

        var recommendation = service.recommend(REQUEST, "session-1").recommendation();

        assertThat(recommendation.weightAdvice()).isNotBlank();
        assertThat(recommendation.suggestedWeightRange()).matches("\\d+(-\\d+)? g");
    }

    @Test
    void theResolvedBagShowsTheStabilityArithmeticForEachDisc() {
        givenRetrieval(vectorResult(List.of(zone)));
        givenGeneration(chose("zone"));

        var resolved = service.recommend(REQUEST, "session-1").analysis().resolvedBag().getFirst();

        // The request bags a 177 g beat-in Buzzz, so wear must show up in the breakdown.
        assertThat(resolved.weightGrams()).isEqualTo(177);
        assertThat(resolved.wear()).isEqualTo("beat in");
        assertThat(resolved.wearStabilityShift()).isNegative();
        assertThat(resolved.stabilityExplanation()).contains("published", "wear", "=");
        assertThat(resolved.publishedStability()).isNotBlank();
    }

    @Test
    void theSelectedGapCarriesItsWeightWindowIntoTheExplainability() {
        givenRetrieval(vectorResult(List.of(zone)));
        givenGeneration(chose("zone"));

        var gaps = service.recommend(REQUEST, "session-1").explainability().flightGaps();

        assertThat(gaps).allSatisfy(gap -> {
            assertThat(gap.targetWeightRange()).isNotBlank();
            assertThat(gap.targetWeightRationale()).isNotBlank();
        });
    }

    @Test
    void refusesToAnswerWhenNothingCouldBeRetrieved() {
        givenRetrieval(vectorResult(List.of()));

        assertThatThrownBy(() -> service.recommend(REQUEST, "session-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No candidate discs are available");
    }

    private GeneratedRecommendation chose(String discId) {
        return new GeneratedRecommendation(discId, "headline", "summary", List.of("reason"), "when", "plastic",
                "weight", "170-174 g", 0.7, GeneratedRecommendation.Source.LANGUAGE_MODEL, "test-reasoning-model", 500, null, false);
    }

    private RetrievalResult vectorResult(List<RetrievedDisc> candidates) {
        return new RetrievalResult(RetrievalResult.RetrievalMode.VECTOR_SIMILARITY,
                "Bag gap: looking for an overstable putter / approach", "test-embedding-model", false,
                candidates, 12, null, "none");
    }

    private RetrievedDisc candidate(String id, String brand, String name, double speed, double glide,
            double turn, double fade, double similarity, int rank) {
        return new RetrievedDisc(id, name, brand, "Putter", speed, glide, turn, fade, "Overstable",
                null, null, "Disc: " + brand + " " + name + ", an overstable approach disc for forehand flex shots.",
                List.of("overstable putter / approach", "forehand flex shots"), "test-embedding-model",
                similarity, 1 - similarity, rank);
    }
}
