package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the model answered for a bare name, kept beside the entry's own values (design D5): kept by every update,
 * shown as low confidence below 0.7, and a rule that disagrees with it shown with both values.
 */
class GlossaryEntryKnowledgeTest {

    private static NameKnowledge person(final Gender gender, final double confidence) {
        return new NameKnowledge(gender, TermType.CHARACTER, null, confidence);
    }

    private static GlossaryEntry ivar(final NameKnowledge known) {
        return new GlossaryEntry(
                "p:Ivar",
                "p",
                "Ivar",
                List.of(),
                false,
                null,
                TargetState.PINNED,
                null,
                TermType.CHARACTER,
                null,
                Origin.LIKELY,
                Gender.UNKNOWN,
                null,
                false,
                false,
                null,
                null,
                known,
                0);
    }

    @Test
    void knowledge_everyUpdate_keepsIt() {
        final GlossaryEntry known = ivar(person(Gender.MALE, 0.8));

        assertThat(known.withTarget("Івар")
                        .withGender(Gender.FEMALE)
                        .withType(TermType.CHARACTER, Origin.USER)
                        .withForms(List.of("Ivara"))
                        .withLocked(true)
                        .knowledge())
                .isEqualTo(person(Gender.MALE, 0.8));
    }

    @ParameterizedTest(name = "[{index}] {0} -> low {1}")
    @CsvSource({"0.55, true", "0.69, true", "0.7, false", "0.9, false"})
    void isLowConfidenceFor_likelyGenderOfTheAnswer_isLowBelowSeventyPercent(
            final double confidence, final boolean low) {
        assertThat(person(Gender.MALE, confidence).isLowConfidenceFor(Gender.MALE, Origin.LIKELY))
                .isEqualTo(low);
    }

    @Test
    void isLowConfidenceFor_genderThePersonSetOrAnotherGender_isNeverLow() {
        assertThat(person(Gender.MALE, 0.4).isLowConfidenceFor(Gender.MALE, Origin.USER))
                .isFalse();
        assertThat(person(Gender.MALE, 0.4).isLowConfidenceFor(Gender.FEMALE, Origin.LIKELY))
                .isFalse();
    }

    @Test
    void disagreesWithRule_ruleMaleAndModelFemale_isTrueAndAnAgreeingRuleIsNot() {
        assertThat(person(Gender.FEMALE, 0.8).disagreesWithRule(Gender.MALE, Origin.RULE))
                .isTrue();
        assertThat(person(Gender.MALE, 0.8).disagreesWithRule(Gender.MALE, Origin.RULE))
                .isFalse();
        assertThat(person(Gender.FEMALE, 0.8).disagreesWithRule(Gender.MALE, Origin.BOOK))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void nameKnowledge_confidenceOutsideZeroToOne_isRejected(final double confidence) {
        assertThatThrownBy(() -> person(Gender.MALE, confidence)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameKnowledge_placeKindOnAPerson_isDropped() {
        assertThat(new NameKnowledge(Gender.MALE, TermType.CHARACTER, PlaceKind.CITY, 0.9).placeKind())
                .isNull();
    }
}
