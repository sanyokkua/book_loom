package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.PronounGender.Evidence;

/**
 * Past-tense verbs after a name add to its pronoun evidence under the unchanged bars: three votes of which 80 % agree
 * decide a gender, five at 90 % decide it with no model. All names are invented.
 */
class PronounGenderEvidenceTest {

    private static List<String> times(final int count, final String word) {
        return Collections.nCopies(count, word);
    }

    // Columns: feminine pronouns, masculine pronouns, feminine verbs, masculine verbs, the gender decided ("NONE" for
    // none) and whether it is strong.
    @ParameterizedTest
    @CsvSource({
        "2, 0, 1, 0, FEMALE, false",
        "2, 1, 0, 0, NONE, false",
        "1, 0, 1, 1, NONE, false",
        "3, 1, 1, 0, FEMALE, false",
        "4, 0, 5, 1, FEMALE, true",
        "0, 0, 0, 3, MALE, false"
    })
    void thresholds_threeAtEightyPercent_decide(
            final int femalePronouns,
            final int malePronouns,
            final int femaleVerbs,
            final int maleVerbs,
            final String expected,
            final boolean strong) {
        final Evidence evidence = new Evidence(
                times(femalePronouns, "она"),
                times(malePronouns, "он"),
                List.of(),
                List.of(),
                times(femaleVerbs, "пришла"),
                times(maleVerbs, "ушёл"));

        assertThat(evidence.dominant())
                .isEqualTo("NONE".equals(expected) ? Optional.empty() : Optional.of(Gender.valueOf(expected)));
        assertThat(evidence.isStrong()).isEqualTo(strong);
    }

    // Three feminine verbs after the name decide her gender with no pronoun at all.
    @Test
    void of_threeFeminineVerbsAfterTheName_decideFemale() {
        final Evidence evidence =
                PronounGender.of("Ольга", List.of("Ольга пришла.", "Ольга сказала тихо.", "Ольга ушла."), "ru");

        assertThat(evidence.female()).isEqualTo(3);
        assertThat(evidence.dominant()).contains(Gender.FEMALE);
    }

    // The verbs are shown beside the pronouns in the compact evidence line.
    @Test
    void compact_verbsAndPronouns_listsBoth() {
        final Evidence evidence = PronounGender.of("Ольга", List.of("Ольга пришла. Она устала.", "Ольга ушла."), "ru");

        assertThat(evidence.compact()).isEqualTo("она ×1, пришла/ушла ×2");
    }

    // A verb inside quoted speech is no vote.
    @Test
    void of_verbInsideSpeech_notCounted() {
        final Evidence evidence = PronounGender.of("Ольга", List.of("«Ольга пришла», сказал он."), "ru");

        assertThat(evidence.total()).isZero();
    }
}
