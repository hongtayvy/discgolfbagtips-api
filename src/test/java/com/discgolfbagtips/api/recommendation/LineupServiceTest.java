package com.discgolfbagtips.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.TestProperties;
import com.discgolfbagtips.api.analysis.BagAnalyzer;
import com.discgolfbagtips.api.analysis.RedundancyAnalyzer;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.bag.BagFittingService;
import com.discgolfbagtips.api.bag.CarryWeightCalculator;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.player.CourseConditions;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.BagDiscRequest;
import com.discgolfbagtips.api.recommendation.dto.BagLineupResponse;
import com.discgolfbagtips.api.recommendation.dto.SlotLineup;
import com.discgolfbagtips.api.retrieval.RetrievalResult;
import com.discgolfbagtips.api.retrieval.RetrievalService;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LineupServiceTest {

    private final BagResolver bagResolver = mock(BagResolver.class);
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final BagFittingService bagFittingService = mock(BagFittingService.class);

    private LineupService service;

    private static final BagAnalysisRequest REQUEST = new BagAnalysisRequest(
            List.of(new BagDiscRequest("buzzz", null, null, "ESP", 177, null)),
            new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.FOREHAND, CourseType.WOODED),
            new CourseConditions(WeatherCondition.WINDY), null, null);

    @BeforeEach
    void setUp() {
        service = new LineupService(bagResolver, new BagAnalyzer(new RedundancyAnalyzer()), retrievalService, embeddingService,
                bagFittingService, new CarryWeightCalculator(), TestProperties.defaults());
        when(bagFittingService.fitting(anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());
        when(embeddingService.provider()).thenReturn("ollama");
        when(embeddingService.model()).thenReturn("test-model");
        when(embeddingService.dimensions()).thenReturn(384);
        when(retrievalService.retrieve(any(), any(), any(), any(), any())).thenReturn(
                new RetrievalResult(RetrievalResult.RetrievalMode.VECTOR_SIMILARITY, "q", "test-model", false,
                        List.of(candidate("zone", "Discraft", "Zone", 1),
                                candidate("harp", "Westside", "Harp", 2),
                                candidate("pig", "Innova", "Pig", 3),
                                candidate("rhyno", "Innova", "Rhyno", 4)),
                        5, null, "none"));
    }

    private void givenBag(List<BagDisc> bag) {
        when(bagResolver.resolve(anyList())).thenReturn(new BagResolver.ResolvedBag(bag, List.of()));
    }

    private BagDisc bagged(String name, String category, double speed, double glide, double turn, double fade) {
        return BagDisc.of(TestDiscs.disc(name, "Innova", name, category, speed, glide, turn, fade), null, null);
    }

    /** The UI renders fixed tabs, so the shape must not depend on what happens to be in the bag. */
    @Test
    void alwaysReturnsAllFourSlotsInThrowingOrder() {
        givenBag(List.of(bagged("Buzzz", "Midrange", 5, 4, -1, 1)));

        BagLineupResponse response = service.buildLineup(REQUEST, "s1");

        assertThat(response.slots()).extracting(SlotLineup::slot)
                .containsExactly("PUTT_AND_APPROACH", "MIDRANGE", "FAIRWAY_DRIVER", "DISTANCE_DRIVER");
        assertThat(response.slots()).allSatisfy(slot ->
                assertThat(slot.stabilityCoverage()).hasSize(5));
    }

    @Test
    void marksSlotsWithNothingInThemAsEmpty() {
        givenBag(List.of(bagged("Buzzz", "Midrange", 5, 4, -1, 1)));

        BagLineupResponse response = service.buildLineup(REQUEST, "s1");
        SlotLineup distance = slot(response, "DISTANCE_DRIVER");

        assertThat(distance.status()).isEqualTo(SlotLineup.Status.EMPTY);
        assertThat(distance.discCount()).isZero();
        assertThat(distance.inBag()).isEmpty();
        assertThat(distance.summary()).contains("Nothing in the bag covers");
    }

    @Test
    void putsEachDiscInTheSlotItsSpeedBelongsTo() {
        givenBag(List.of(
                bagged("Aviar", "Putter", 2, 3, 0, 1),
                bagged("Buzzz", "Midrange", 5, 4, -1, 1),
                bagged("Teebird", "Control Driver", 7, 5, 0, 2),
                bagged("Destroyer", "Distance Driver", 12, 5, -1, 3)));

        BagLineupResponse response = service.buildLineup(REQUEST, "s1");

        assertThat(slot(response, "PUTT_AND_APPROACH").inBag()).extracting("name").containsExactly("Aviar");
        assertThat(slot(response, "MIDRANGE").inBag()).extracting("name").containsExactly("Buzzz");
        assertThat(slot(response, "FAIRWAY_DRIVER").inBag()).extracting("name").containsExactly("Teebird");
        assertThat(slot(response, "DISTANCE_DRIVER").inBag()).extracting("name").containsExactly("Destroyer");
        assertThat(response.discCount()).isEqualTo(4);
    }

    @Test
    void attachesSuggestionsToGapsAndCapsHowManyAreOffered() {
        givenBag(List.of(bagged("Buzzz", "Midrange", 5, 4, -1, 1)));

        BagLineupResponse response = service.buildLineup(REQUEST, "s1");
        List<SlotLineup.SlotGap> withSuggestions = response.slots().stream()
                .flatMap(s -> s.gaps().stream())
                .filter(g -> !g.suggestions().isEmpty())
                .toList();

        assertThat(withSuggestions).isNotEmpty();
        // Four candidates come back from retrieval; only three are offered per hole.
        assertThat(withSuggestions).allSatisfy(gap -> {
            assertThat(gap.suggestions()).hasSizeLessThanOrEqualTo(3);
            assertThat(gap.suggestions().getFirst().deltaVsTarget()).isNotNull();
            assertThat(gap.targetWeightRange()).isNotBlank();
        });
    }

    /** Retrieval is the expensive step, so low-severity gaps are listed but not searched for. */
    @Test
    void doesNotSpendARetrievalOnEveryGapItFinds() {
        givenBag(List.of(bagged("Buzzz", "Midrange", 5, 4, -1, 1)));

        BagLineupResponse response = service.buildLineup(REQUEST, "s1");

        assertThat(response.explainability().gapsRetrieved())
                .isLessThanOrEqualTo(response.explainability().gapsConsidered())
                .isLessThanOrEqualTo(10);
        verify(retrievalService, times(response.explainability().gapsRetrieved()))
                .retrieve(any(), any(), any(), any(), any());
    }

    @Test
    void reportsDegradedRetrievalRatherThanHidingIt() {
        givenBag(List.of(bagged("Buzzz", "Midrange", 5, 4, -1, 1)));
        when(retrievalService.retrieve(any(), any(), any(), any(), any())).thenReturn(
                new RetrievalResult(RetrievalResult.RetrievalMode.FLIGHT_NUMBER_FALLBACK, "q", "flight-numbers",
                        false, List.of(candidate("zone", "Discraft", "Zone", 1)), 2, "embeddings unavailable",
                        "none"));

        BagLineupResponse response = service.buildLineup(REQUEST, "s1");

        assertThat(response.explainability().retrievalMode()).isEqualTo("FLIGHT_NUMBER_FALLBACK");
        assertThat(response.explainability().degradations())
                .anyMatch(note -> note.contains("flight-number search"));
    }

    @Test
    void anEmptyBagReportsEveryStatusAsEmpty() {
        givenBag(List.of());

        BagLineupResponse response = service.buildLineup(
                new BagAnalysisRequest(List.of(), REQUEST.profile(), REQUEST.conditions(), null, null), "s1");

        assertThat(response.slots()).allSatisfy(s ->
                assertThat(s.status()).isEqualTo(SlotLineup.Status.EMPTY));
        assertThat(response.coverageScore()).isZero();
    }

    private SlotLineup slot(BagLineupResponse response, String name) {
        return response.slots().stream().filter(s -> s.slot().equals(name)).findFirst().orElseThrow();
    }

    private RetrievedDisc candidate(String id, String brand, String name, int rank) {
        return new RetrievedDisc(id, name, brand, "Putter", 4, 3, 0, 3, "Overstable", null, null,
                "passage", List.of("overstable putter / approach"), "test-model", 0.9 - rank * 0.01,
                0.1 + rank * 0.01, rank);
    }
}
