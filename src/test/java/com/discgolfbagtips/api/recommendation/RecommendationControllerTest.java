package com.discgolfbagtips.api.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.discgolfbagtips.api.TestProperties;
import com.discgolfbagtips.api.cache.AnalysisCache;
import com.discgolfbagtips.api.common.GlobalExceptionHandler;
import com.discgolfbagtips.api.recommendation.dto.RecommendationResponse;
import com.discgolfbagtips.api.session.SessionState;
import com.discgolfbagtips.api.session.SessionStateService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Contract-level checks on the endpoint the front end calls: what a bad request looks like coming
 * back out, and that a rejected request never reaches the (expensive) pipeline.
 */
class RecommendationControllerTest {

    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final LineupService lineupService = mock(LineupService.class);
    private final SessionStateService sessionStateService = mock(SessionStateService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecommendationController(recommendationService, lineupService,
                        sessionStateService, new AnalysisCache(TestProperties.defaults())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(sessionStateService.sessionId(any())).thenReturn("session-1");
        when(sessionStateService.current(any())).thenReturn(SessionState.empty());
    }

    private static final String VALID_BODY = """
            {"bag":[{"discId":"buzzz","plastic":"ESP"}],
             "profile":{"skillLevel":"INTERMEDIATE","throwingStyle":"FOREHAND","courseType":"WOODED"},
             "conditions":{"weather":"WINDY"}}
            """;

    @Test
    void acceptsAWellFormedRequest() throws Exception {
        when(recommendationService.recommend(any(), anyString())).thenReturn(
                new RecommendationResponse("session-1", Instant.now(), null, List.of(), null, null));

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"));

        verify(sessionStateService).recordRecommendation(any(), any(), any());
    }

    @Test
    void rejectsAMissingProfileWithAProblemDetail() throws Exception {
        String body = """
                {"bag":[],"conditions":{"weather":"NORMAL"}}
                """;

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.profile").exists());

        verify(recommendationService, never()).recommend(any(), anyString());
    }

    @Test
    void rejectsABagEntryThatIdentifiesNothing() throws Exception {
        String body = """
                {"bag":[{"plastic":"Star"}],
                 "profile":{"skillLevel":"BEGINNER","throwingStyle":"BACKHAND","courseType":"OPEN"},
                 "conditions":{"weather":"NORMAL"}}
                """;

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(recommendationService, never()).recommend(any(), anyString());
    }

    @Test
    void acceptsWeightAndWearOnTheWire() throws Exception {
        when(recommendationService.recommend(any(), anyString())).thenReturn(
                new RecommendationResponse("session-1", Instant.now(), null, List.of(), null, null));

        String body = """
                {"bag":[{"name":"Firebird","brand":"Innova","plastic":"DX",
                         "weightGrams":173,"wear":"WELL_WORN"}],
                 "profile":{"skillLevel":"ADVANCED","throwingStyle":"FOREHAND","courseType":"MIXED"},
                 "conditions":{"weather":"WINDY"}}
                """;

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAWeightThatIsNotAGolfDisc() throws Exception {
        String body = """
                {"bag":[{"name":"Buzzz","weightGrams":420}],
                 "profile":{"skillLevel":"ADVANCED","throwingStyle":"BOTH","courseType":"MIXED"},
                 "conditions":{"weather":"NORMAL"}}
                """;

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verify(recommendationService, never()).recommend(any(), anyString());
    }

    @Test
    void rejectsAnUnknownWearState() throws Exception {
        String body = """
                {"bag":[{"name":"Buzzz","wear":"THRASHED"}],
                 "profile":{"skillLevel":"ADVANCED","throwingStyle":"BOTH","courseType":"MIXED"},
                 "conditions":{"weather":"NORMAL"}}
                """;

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"))
                .andExpect(jsonPath("$.field").value("bag[0].wear"))
                .andExpect(jsonPath("$.accepted").isArray());

        verify(recommendationService, never()).recommend(any(), anyString());
    }

    @Test
    void reportsMalformedJsonAsTheCallersMistake() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bag\": [ "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://discgolfbagtips.com/problems/malformed-request"));

        verify(recommendationService, never()).recommend(any(), anyString());
    }

    @Test
    void rejectsAnOversizedBag() throws Exception {
        String entries = String.join(",", java.util.Collections.nCopies(31, "{\"name\":\"Buzzz\"}"));
        String body = """
                {"bag":[%s],
                 "profile":{"skillLevel":"ADVANCED","throwingStyle":"BOTH","courseType":"MIXED"},
                 "conditions":{"weather":"HOT"}}
                """.formatted(entries);

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(recommendationService, never()).recommend(any(), anyString());
    }

    @Test
    void returnsNotFoundWhenTheSessionHasNoRecommendationYet() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://discgolfbagtips.com/problems/not-found"));
    }
}
