package com.discgolfbagtips.api.bag;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.catalog.WearState;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class CarryWeightCalculatorTest {

    private final CarryWeightCalculator calculator = new CarryWeightCalculator();

    private BagDisc disc(String name, double speed, Integer grams) {
        return new BagDisc(TestDiscs.disc(name, "Innova", name, "Midrange", speed, 4, -1, 1),
                null, null, grams, WearState.NEW);
    }

    private BagModel bag(String brand, String model, Integer weightGrams, int capacity) {
        try {
            var constructor = BagModel.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            BagModel instance = constructor.newInstance();
            for (Object[] pair : new Object[][] {
                    {"id", 1L}, {"brand", brand}, {"model", model}, {"slug", model.toLowerCase()},
                    {"emptyWeightGrams", weightGrams}, {"discCapacityMax", capacity},
                    {"buildTier", "PREMIUM"}, {"bagType", "BACKPACK"}, {"source", "MANUAL_RESEARCH"}}) {
                Field field = BagModel.class.getDeclaredField((String) pair[0]);
                field.setAccessible(true);
                field.set(instance, pair[1]);
            }
            return instance;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void sumsGivenDiscWeightsAndTheBag() {
        CarryWeight result = calculator.calculate(
                List.of(disc("a", 5, 175), disc("b", 5, 180), disc("c", 5, 170)),
                bag("GRIPeq", "AX6", 2268, 22));

        assertThat(result.discWeightGrams()).isEqualTo(525);
        assertThat(result.bagWeightGrams()).isEqualTo(2268);
        assertThat(result.totalGrams()).isEqualTo(2793);
        assertThat(result.estimated()).isFalse();
        assertThat(result.assumedWeights()).isZero();
        assertThat(result.overpacked()).isFalse();
    }

    /** A total is still useful with weights missing, but it must not read as measured. */
    @Test
    void marksTheTotalEstimatedWhenAnyDiscWeightWasAssumed() {
        CarryWeight result = calculator.calculate(
                List.of(disc("a", 5, 175), disc("b", 5, null)),
                bag("GRIPeq", "AX6", 2268, 22));

        assertThat(result.estimated()).isTrue();
        assertThat(result.assumedWeights()).isEqualTo(1);
        assertThat(result.notes()).anyMatch(n -> n.contains("no weight given"));
        // The assumed disc used the midrange reference weight rather than zero.
        assertThat(result.discWeightGrams()).isGreaterThan(175 + 150);
    }

    @Test
    void reportsDiscLoadAloneWhenTheBagPublishesNoWeight() {
        CarryWeight result = calculator.calculate(
                List.of(disc("a", 5, 175)), bag("Zuca", "Disc Golf Cart", null, 32));

        assertThat(result.bagWeightGrams()).isNull();
        assertThat(result.totalGrams()).isNull();
        assertThat(result.discWeightGrams()).isEqualTo(175);
        assertThat(result.notes()).anyMatch(n -> n.contains("does not publish an empty weight"));
        assertThat(result.describe()).contains("needs a bag with a published empty weight");
    }

    @Test
    void flagsOverpacking() {
        List<BagDisc> twelve = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> disc("d" + i, 5, 175)).toList();

        CarryWeight result = calculator.calculate(twelve, bag("GRIPeq", "G-Series", 1089, 10));

        assertThat(result.overpacked()).isTrue();
        assertThat(result.capacity()).isEqualTo(10);
        assertThat(result.notes()).anyMatch(n -> n.contains("exceeds the 10 this bag is rated for"));
    }

    @Test
    void warnsWhenTheBagIsEssentiallyFullWithoutBeingOver() {
        List<BagDisc> ten = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> disc("d" + i, 5, 175)).toList();

        CarryWeight result = calculator.calculate(ten, bag("GRIPeq", "G-Series", 1089, 10));

        assertThat(result.overpacked()).isFalse();
        assertThat(result.notes()).anyMatch(n -> n.contains("essentially full"));
    }

    @Test
    void worksWithNoBagAtAll() {
        CarryWeight result = calculator.calculate(List.of(disc("a", 5, 175)), null);

        assertThat(result.bagWeightGrams()).isNull();
        assertThat(result.capacity()).isNull();
        assertThat(result.overpacked()).isFalse();
        assertThat(result.discWeightGrams()).isEqualTo(175);
    }

    @Test
    void convertsToPoundsForDisplay() {
        assertThat(CarryWeight.gramsToPounds(453)).isEqualTo(1.0);
        assertThat(CarryWeight.gramsToPounds(2268)).isEqualTo(5.0);
    }
}
