package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.eval.BareNames.BareName;

/** Arm 4a's rule: confident answers at most 5 % wrong and every unisex name below 0.7; and the committed set's shape. */
class BareNamesScoreTest {

    private static final List<BareName> NAMES = List.of(
            new BareName("Aldo Fenn", "en", Gender.MALE, false),
            new BareName("Mirela Stowe", "en", Gender.FEMALE, false),
            new BareName("Quill Dace", "en", null, true));

    private static NameKnowledge said(final Gender gender, final double confidence) {
        return new NameKnowledge(gender, TermType.CHARACTER, null, confidence);
    }

    @Test
    void of_confidentWrongAndUnisexHigh_failsWithBothNamed() {
        final BareNamesScore score = BareNamesScore.of(
                NAMES,
                Map.of(
                        "Aldo Fenn", said(Gender.FEMALE, 0.9),
                        "Mirela Stowe", said(Gender.FEMALE, 0.8),
                        "Quill Dace", said(Gender.MALE, 0.7)));

        assertThat(score.confident()).isEqualTo(2);
        assertThat(score.wrongTerms()).containsExactly("Aldo Fenn");
        assertThat(score.unisexConfidentTerms()).containsExactly("Quill Dace");
        assertThat(score.passes()).isFalse();
    }

    @Test
    void of_wrongOnlyBelowTheBarAndUnisexLow_passes() {
        final BareNamesScore score = BareNamesScore.of(
                NAMES,
                Map.of(
                        "Aldo Fenn", said(Gender.FEMALE, 0.6),
                        "Mirela Stowe", said(Gender.FEMALE, 0.95),
                        "Quill Dace", said(Gender.MALE, 0.5)));

        assertThat(score.confident()).isEqualTo(1);
        assertThat(score.confidentWrong()).isZero();
        assertThat(score.passes()).isTrue();
    }

    @Test
    void of_unansweredName_isCountedAsNotAnswered() {
        final BareNamesScore score = BareNamesScore.of(NAMES, Map.of("Mirela Stowe", said(Gender.FEMALE, 0.9)));

        assertThat(score.answered()).isEqualTo(1);
        assertThat(score.unisex()).isZero();
    }

    @Test
    void load_committedSet_holdsSixtyOneInventedNamesFifteenUnisex() {
        final List<BareName> names = BareNames.load();

        assertThat(names).hasSize(61);
        assertThat(names).filteredOn(BareName::unisex).hasSize(15);
        assertThat(names).extracting(BareName::language).containsOnly("en", "ru");
    }
}
