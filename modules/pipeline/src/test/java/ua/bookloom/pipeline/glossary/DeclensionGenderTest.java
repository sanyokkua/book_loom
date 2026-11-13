package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;

/**
 * A name whose attested forms decline by a row of one gender, a row the language file lists in
 * {@code paradigmGender}, gets that gender by rule; a contrary vote, an unlisted row or a non-person type gets none.
 * All names are invented.
 */
class DeclensionGenderTest {

    private static final List<String> NEUTRAL = List.of("Ключ Велемира лежал на столе.", "Утром Велемир у двери.");

    private static GlossaryEntry entry(final String term, final TermType type, final String... forms) {
        return GlossaryEntry.of("p1:" + term, "p1", term, null, type, Gender.UNKNOWN, false)
                .withForms(List.of(forms));
    }

    // A consonant name with an attested genitive declines like a man's name, so it is male by rule.
    @ParameterizedTest
    @CsvSource({"Велемир, Велемира, MALE", "Рудакова, Рудаковой, FEMALE", "Тарн, Тарна, MALE"})
    void seeded_declinedFormOfAListedRow_setsTheGenderByRule(
            final String term, final String form, final Gender expected) {
        final GlossaryEntry seeded = DeclensionGender.seeded(entry(term, TermType.OTHER, form), NEUTRAL, "ru");

        assertThat(seeded)
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin, GlossaryEntry::type)
                .containsExactly(expected, Origin.RULE, TermType.OTHER);
    }

    // A feminine verb right after the name contradicts the declension, so nothing is set.
    @Test
    void seeded_contraryVote_setsNothing() {
        final List<String> texts = List.of("Велемир пришла поздно.", "Ключ Велемира лежал на столе.");

        assertThat(DeclensionGender.seeded(entry("Велемир", TermType.CHARACTER, "Велемира"), texts, "ru")
                        .gender())
                .isEqualTo(Gender.UNKNOWN);
    }

    // A name in -а declines like a woman's but may be a man's (Никита), so its row is not listed and nothing is set.
    @Test
    void seeded_unlistedRow_setsNothing() {
        assertThat(DeclensionGender.seeded(entry("Горята", TermType.CHARACTER, "Горяты"), NEUTRAL, "ru")
                        .gender())
                .isEqualTo(Gender.UNKNOWN);
    }

    // A place, a known gender, a nominative-only entry and a language with no table are left as they are.
    @Test
    void seeded_ineligibleEntries_areUnchanged() {
        final GlossaryEntry place = entry("Велемир", TermType.PLACE, "Велемира");
        final GlossaryEntry known =
                entry("Велемир", TermType.CHARACTER, "Велемира").withGender(Gender.FEMALE);
        final GlossaryEntry bare = entry("Велемир", TermType.CHARACTER);

        assertThat(List.of(
                        DeclensionGender.seeded(place, NEUTRAL, "ru"),
                        DeclensionGender.seeded(known, NEUTRAL, "ru"),
                        DeclensionGender.seeded(bare, NEUTRAL, "ru"),
                        DeclensionGender.seeded(bare.withForms(List.of("Велемира")), NEUTRAL, "en")))
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple(Gender.UNKNOWN, null),
                        tuple(Gender.FEMALE, Origin.USER),
                        tuple(Gender.UNKNOWN, null),
                        tuple(Gender.UNKNOWN, null));
    }
}
