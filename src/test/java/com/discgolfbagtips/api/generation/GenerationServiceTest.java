package com.discgolfbagtips.api.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagAnalyzer;
import com.discgolfbagtips.api.analysis.RedundancyAnalyzer;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.common.UpstreamServiceException;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GenerationServiceTest {

    private static final PlayerProfile PROFILE =
            new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.BACKHAND, CourseType.MIXED);

    private final ChatModelClient chatModelClient = mock(ChatModelClient.class);
    private final GenerationService service =
            new GenerationService(chatModelClient, new RecommendationPromptBuilder(), JsonMapper.builder().build());

    private BagAnalysis analysis;
    private BagGap gap;

    private final List<RetrievedDisc> candidates = List.of(
            candidate("zone", "Discraft", "Zone", 4, 3, 0, 3, 0.81, 1),
            candidate("harp", "Westside", "Harp", 5, 4, 0, 4, 0.77, 2));

    @BeforeEach
    void setUp() {
        BagDisc bagged = BagDisc.of(
                TestDiscs.disc("buzzz", "Discraft", "Buzzz", "Midrange", 5, 4, -1, 1), null, null);
        analysis = new BagAnalyzer(new RedundancyAnalyzer()).analyze(List.of(bagged), PROFILE, WeatherCondition.NORMAL, List.of());
        gap = analysis.primaryGap();

        when(chatModelClient.available()).thenReturn(true);
        when(chatModelClient.model()).thenReturn("test-reasoning-model");
        when(chatModelClient.provider()).thenReturn("groq");
    }

    @Test
    void usesTheModelAnswerWhenItPicksARetrievedDisc() {
        when(chatModelClient.complete(anyString(), anyString())).thenReturn("""
                {"discId":"harp","headline":"A dependable overstable approach",
                 "summary":"Fills the overstable hole.","reasoning":["one","two"],
                 "whenToThrowIt":"Forehand approaches.","plasticAdvice":"VIP.","confidence":0.82}
                """);

        GeneratedRecommendation recommendation =
                service.generate(analysis, gap, PROFILE, WeatherCondition.NORMAL, candidates);

        assertThat(recommendation.discId()).isEqualTo("harp");
        assertThat(recommendation.source()).isEqualTo(GeneratedRecommendation.Source.LANGUAGE_MODEL);
        assertThat(recommendation.reasoning()).containsExactly("one", "two");
        assertThat(recommendation.confidence()).isEqualTo(0.82);
        assertThat(recommendation.note()).isNull();
    }

    @Test
    void overrulesTheModelWhenItNamesADiscThatWasNotRetrieved() {
        when(chatModelClient.complete(anyString(), anyString())).thenReturn(
                "{\"discId\":\"firebird\",\"summary\":\"Trust me.\",\"confidence\":0.99}");

        GeneratedRecommendation recommendation =
                service.generate(analysis, gap, PROFILE, WeatherCondition.NORMAL, candidates);

        assertThat(recommendation.discId()).isEqualTo("zone");
        assertThat(recommendation.source()).isEqualTo(GeneratedRecommendation.Source.LANGUAGE_MODEL_CORRECTED);
        assertThat(recommendation.note()).contains("outside the retrieved candidates");
    }

    @Test
    void unwrapsFencedJson() {
        when(chatModelClient.complete(anyString(), anyString())).thenReturn("""
                ```json
                {"discId":"harp","summary":"Fenced anyway.","confidence":0.5}
                ```
                """);

        GeneratedRecommendation recommendation =
                service.generate(analysis, gap, PROFILE, WeatherCondition.NORMAL, candidates);

        assertThat(recommendation.discId()).isEqualTo("harp");
        assertThat(recommendation.source()).isEqualTo(GeneratedRecommendation.Source.LANGUAGE_MODEL);
    }

    @Test
    void fallsBackWhenTheModelReturnsSomethingThatIsNotJson() {
        when(chatModelClient.complete(anyString(), anyString())).thenReturn("Sorry, I can't help with that.");

        GeneratedRecommendation recommendation =
                service.generate(analysis, gap, PROFILE, WeatherCondition.NORMAL, candidates);

        assertThat(recommendation.source()).isEqualTo(GeneratedRecommendation.Source.RULE_BASED_FALLBACK);
        assertThat(recommendation.discId()).isEqualTo("zone");
        assertThat(recommendation.note()).contains("did not return valid JSON");
    }

    @Test
    void fallsBackWhenTheModelIsUnreachable() {
        when(chatModelClient.complete(anyString(), anyString()))
                .thenThrow(new UpstreamServiceException("groq", "connection reset"));

        GeneratedRecommendation recommendation =
                service.generate(analysis, gap, PROFILE, WeatherCondition.NORMAL, candidates);

        assertThat(recommendation.source()).isEqualTo(GeneratedRecommendation.Source.RULE_BASED_FALLBACK);
        assertThat(recommendation.note()).contains("connection reset");
        assertThat(recommendation.reasoning()).isNotEmpty();
        assertThat(recommendation.summary()).contains("Discraft Zone");
    }

    @Test
    void doesNotCallTheModelAtAllWhenNoKeyIsConfigured() {
        when(chatModelClient.available()).thenReturn(false);

        GeneratedRecommendation recommendation =
                service.generate(analysis, gap, PROFILE, WeatherCondition.NORMAL, candidates);

        verify(chatModelClient, never()).complete(anyString(), anyString());
        assertThat(recommendation.source()).isEqualTo(GeneratedRecommendation.Source.RULE_BASED_FALLBACK);
        assertThat(recommendation.note()).contains("No reasoning-model API key");
    }

    @Test
    void ruleBasedProseCitesTheGapAndTheFlightNumbers() {
        when(chatModelClient.available()).thenReturn(false);

        GeneratedRecommendation recommendation =
                service.generate(analysis, gap, PROFILE, WeatherCondition.RAINY, candidates);

        assertThat(recommendation.reasoning()).anyMatch(line -> line.contains("flight delta")
                || line.contains("matching the"));
        assertThat(recommendation.plasticAdvice()).contains("grippy");
    }

    @Test
    void stripsFencesOnlyWhenPresent() {
        assertThat(GenerationService.stripCodeFence("{\"a\":1}")).isEqualTo("{\"a\":1}");
        assertThat(GenerationService.stripCodeFence("```\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
    }

    private RetrievedDisc candidate(String id, String brand, String name, double speed, double glide,
            double turn, double fade, double similarity, int rank) {
        return new RetrievedDisc(id, name, brand, "Putter", speed, glide, turn, fade, "Overstable",
                null, null, "Disc: " + brand + " " + name + ", an overstable approach disc.",
                List.of("overstable putter / approach"), "test-embedding-model", similarity,
                1 - similarity, rank);
    }
}
