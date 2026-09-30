package com.discgolfbagtips.api.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagAnalyzer;
import com.discgolfbagtips.api.analysis.RedundancyAnalyzer;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.PlasticFamily;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingTextBuilderTest {

    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();
    private final BagAnalyzer analyzer = new BagAnalyzer(new RedundancyAnalyzer());

    private final Disc buzzz = TestDiscs.disc("buzzz", "Discraft", "Buzzz", "Midrange", 5, 4, -1, 1);
    private final PlasticType esp = TestDiscs.plastic("Discraft", "ESP", PlasticFamily.GRIPPY_PREMIUM,
            0.1, 4, 4, "Premium durability with a soft, grippy surface.");

    @Test
    void aDiscPassageCarriesNumbersPlasticAndVocabulary() {
        String passage = builder.forDisc(buzzz, esp);

        // the numbers themselves
        assertThat(passage).contains("speed 5", "glide 4", "turn -1", "fade 1");
        // the plastic and its effect on flight
        assertThat(passage).contains("ESP", "grippy premium", "durability 4/5", "grip 4/5");
        // the terminology that makes the vector worth more than the four numbers
        assertThat(passage).contains("Thrown for:", "In wind:", "Slot: midrange");
        assertThat(passage).startsWith("Disc: Discraft Buzzz");
    }

    @Test
    void plasticChangesThePassage() {
        PlasticType baseGrade = TestDiscs.plastic("Discraft", "Pro-D", PlasticFamily.BASE, -0.3, 1, 5,
                "Base blend, seasons fast.");

        String inEsp = builder.forDisc(buzzz, esp);
        String inProD = builder.forDisc(buzzz, baseGrade);

        assertThat(inEsp).isNotEqualTo(inProD);
        assertThat(inProD).contains("more understable than its published numbers");
        assertThat(inEsp).contains("more overstable than its published numbers");
    }

    @Test
    void aDiscWithNoPlasticStillProducesAUsablePassage() {
        String passage = builder.forDisc(buzzz, null);

        assertThat(passage).doesNotContain("Plastic:");
        assertThat(passage).contains("Flight numbers:", "Thrown for:", "Keywords:");
    }

    @Test
    void theQueryPassageIsWrittenInTheSameRegisterAsADocument() {
        BagDisc bagged = BagDisc.of(buzzz, esp, "ESP");
        BagAnalysis analysis = analyzer.analyze(List.of(bagged),
                new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.FOREHAND, CourseType.WOODED),
                WeatherCondition.WINDY, List.of());

        String query = builder.forGap(analysis.primaryGap(), analysis,
                new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.FOREHAND, CourseType.WOODED),
                WeatherCondition.WINDY);

        assertThat(query).startsWith("Bag gap:");
        assertThat(query).contains("Target flight numbers around speed");
        assertThat(query).contains("Thrown for:", "In wind:", "Keywords:");
        assertThat(query).contains("forehand");
        assertThat(query).contains("windy");
        // The bag is echoed so retrieval knows what is already covered.
        assertThat(query).contains("Already covered in the bag:");
    }

    @Test
    void wetConditionsAskForGrip() {
        BagAnalysis analysis = analyzer.analyze(List.of(BagDisc.of(buzzz, esp, "ESP")),
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.MIXED),
                WeatherCondition.RAINY, List.of());

        String query = builder.forGap(analysis.primaryGap(), analysis,
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.MIXED),
                WeatherCondition.RAINY);

        assertThat(query).contains("Grippy, tacky or gummy plastic is preferred");
    }

    @Test
    void theQueryAsksForAWeightAsWellAsAFlight() {
        BagAnalysis analysis = analyzer.analyze(List.of(BagDisc.of(buzzz, esp, "ESP")),
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.OPEN),
                WeatherCondition.NORMAL, List.of());

        String query = builder.forGap(analysis.primaryGap(), analysis,
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.OPEN),
                WeatherCondition.NORMAL);

        assertThat(query).contains("Preferred weight");
        assertThat(query).contains("carries further");
    }

    /**
     * Weight and wear are per-instance, so they belong in the query and the prompt but never in the
     * stored per-mold document — one vector per mold, adjusted arithmetically at analysis time.
     */
    @Test
    void theStoredDiscPassageStaysFreeOfPerInstanceDetail() {
        String passage = builder.forDisc(buzzz, esp);

        assertThat(passage).doesNotContain("beat in", "well worn", " g,", "Preferred weight");
    }

    @Test
    void descriptorsAreShortAndReusableAsCitations() {
        List<String> descriptors = builder.descriptors(buzzz, esp);

        assertThat(descriptors).isNotEmpty();
        assertThat(descriptors).contains("stable midrange");
        assertThat(descriptors).allSatisfy(descriptor -> assertThat(descriptor).isNotBlank());
    }
}
