package com.discgolfbagtips.api.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalFiltersTest {

    @Test
    void noFilterMeansNoRestriction() {
        RetrievalFilters none = RetrievalFilters.none();

        assertThat(none.isEmpty()).isTrue();
        assertThat(none.restrictsBrands()).isFalse();
        assertThat(none.describe()).isEqualTo("none");
    }

    /** Brand names arrive from a UI, so casing and stray whitespace must not change the result. */
    @Test
    void normalisesBrandInput() {
        RetrievalFilters filters = new RetrievalFilters(
                Arrays.asList("  Innova ", "DISCRAFT", "innova", null, "  "), List.of(), null);

        assertThat(filters.brands()).containsExactly("innova", "discraft");
        assertThat(filters.restrictsBrands()).isTrue();
    }

    @Test
    void toleratesNullLists() {
        RetrievalFilters filters = new RetrievalFilters(null, null, null);

        assertThat(filters.brands()).isEmpty();
        assertThat(filters.excludeBrands()).isEmpty();
        assertThat(filters.isEmpty()).isTrue();
    }

    @Test
    void describesWhatWasAppliedForTheAuditTrail() {
        RetrievalFilters filters =
                new RetrievalFilters(List.of("Innova"), List.of("Prodigy"), 9.0);

        assertThat(filters.describe())
                .contains("brands in [innova]")
                .contains("excluding [prodigy]")
                .contains("speed <= 9.0");
        assertThat(filters.isEmpty()).isFalse();
    }

    @Test
    void aSpeedCeilingAloneStillCountsAsAFilter() {
        RetrievalFilters filters = new RetrievalFilters(List.of(), List.of(), 7.0);

        assertThat(filters.isEmpty()).isFalse();
        assertThat(filters.restrictsBrands()).isFalse();
    }
}
