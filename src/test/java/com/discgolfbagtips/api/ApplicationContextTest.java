package com.discgolfbagtips.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.analysis.BagAnalyzer;
import com.discgolfbagtips.api.catalog.CatalogController;
import com.discgolfbagtips.api.config.RateLimitFilter;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.generation.ChatModelClient;
import com.discgolfbagtips.api.generation.GenerationService;
import com.discgolfbagtips.api.recommendation.RecommendationController;
import com.discgolfbagtips.api.recommendation.RecommendationService;
import com.discgolfbagtips.api.retrieval.EmbeddingBackfillJob;
import com.discgolfbagtips.api.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the whole bean graph wires up: controllers, the three pipeline sub-layers, the scheduled
 * jobs, the rate-limit filter and the resilience4j aspects. Runs against H2, so it says nothing
 * about the pgvector SQL — that is exercised against a real Postgres.
 */
@SpringBootTest
@ActiveProfiles("contexttest")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ChatModelClient chatModelClient;

    @Test
    void everyLayerOfThePipelineIsWired() {
        assertThat(context.getBean(RecommendationController.class)).isNotNull();
        assertThat(context.getBean(CatalogController.class)).isNotNull();
        assertThat(context.getBean(RecommendationService.class)).isNotNull();
        assertThat(context.getBean(BagAnalyzer.class)).isNotNull();
        assertThat(context.getBean(EmbeddingService.class)).isNotNull();
        assertThat(context.getBean(RetrievalService.class)).isNotNull();
        assertThat(context.getBean(GenerationService.class)).isNotNull();
        assertThat(context.getBean(EmbeddingBackfillJob.class)).isNotNull();
        assertThat(context.getBean(RateLimitFilter.class)).isNotNull();
    }

    @Test
    void withNoCredentialsTheServiceStillStartsAndSaysSoHonestly() {
        assertThat(embeddingService.stubbed()).isTrue();
        assertThat(embeddingService.provider()).isEqualTo("local");
        assertThat(embeddingService.dimensions()).isEqualTo(384);
        assertThat(chatModelClient.available()).isFalse();
    }

    @Test
    void oneRestClientIsConfiguredPerUpstream() {
        assertThat(context.getBean("discItRestClient")).isNotNull();
        assertThat(context.getBean("embeddingRestClient")).isNotNull();
        assertThat(context.getBean("generationRestClient")).isNotNull();
    }
}
