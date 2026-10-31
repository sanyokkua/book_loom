package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.TargetValidator.Flag;
import ua.bookloom.pipeline.glossary.TargetValidator.Reason;
import ua.bookloom.pipeline.glossary.TargetValidator.Verdict;

/**
 * A suggested target checked against the target language's own rules before anything accepts it (glossary "Check a
 * suggested target against the target language's rules", "Flag a target that is not a dictionary form"). Invented
 * names only.
 */
class TargetValidatorTest {

    private static final TargetValidator UKRAINIAN = validator("uk", Map.of());

    @Test
    void validate_latinLetterWithoutIdentityRule_isRejected() {
        // The last letter is a Latin k, which reads right in a Cyrillic word and matches nothing.
        final Verdict mixed = UKRAINIAN.validate(name("Velk", TermType.CHARACTER), "Вел" + "k");
        final Verdict identifier = UKRAINIAN.validate(name("ZX-81", TermType.OTHER), "ZX-81");

        assertThat(mixed.rejections()).contains(Reason.LATIN);
        assertThat(identifier.rejections()).isEmpty();
    }

    @Test
    void validate_forbiddenLetter_isRejectedWithItsReason() {
        final Verdict verdict = UKRAINIAN.validate(name("Sinn", TermType.CHARACTER), "Сын");

        assertThat(verdict.rejections()).containsExactly(Reason.FORBIDDEN_LETTER);
        assertThat(verdict.reasons()).containsExactly("letter not in the target alphabet");
    }

    @Test
    void validate_noCapitalForACapitalisedTerm_isRejected() {
        final Verdict verdict = UKRAINIAN.validate(name("Hale", TermType.CHARACTER), "гейл");

        assertThat(verdict.reasons()).containsExactly("capitalisation differs");
    }

    @ParameterizedTest(name = "{0} -> {1}: rejected {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Varno City|Варно|true",
                "Varno City|Варно Сіті|false",
                "Varno City|місто Варно|false",
                "Baker Street|Бейкер-стріт|false",
                "Smith-Jones|Сміт-Джонс|false",
            })
    void validate_wordCount_countsTheHyphenatedPartsOnBothSides(
            final String term, final String target, final boolean rejected) {
        final Verdict verdict = UKRAINIAN.validate(name(term, TermType.PLACE), target);

        assertThat(verdict.rejections().contains(Reason.WORD_COUNT)).isEqualTo(rejected);
        assertThat(verdict.flags().contains(Flag.WORD_COUNT_DIFFERS)).isEqualTo(rejected);
    }

    @Test
    void validate_hardGConvention_foldsGAndGhe() {
        final TargetValidator plainG = validator("uk", Map.of("hardG", "г"));
        final GlossaryEntry pinned = name("Garret", TermType.CHARACTER).withTarget("Гаррет");

        final Verdict otherAlternative = plainG.validate(name("Garcia", TermType.CHARACTER), "Ґарсія");
        final Verdict chosenAlternative = plainG.validate(name("Garcia", TermType.CHARACTER), "Гарсія");
        final Verdict foldedForm = UKRAINIAN.validate(pinned, "Ґаррета");

        assertThat(otherAlternative.reasons()).containsExactly("hard g is spelled ґ, the book uses г");
        assertThat(chosenAlternative.rejections()).isEmpty();
        assertThat(foldedForm.rejections()).isEmpty();
    }

    @Test
    void validate_pinnedRespelling_isRejectedButCorrectGeneratedFormPasses() {
        final GlossaryEntry pinned = name("Alexei", TermType.CHARACTER).withTarget("Алєксєй");

        final Verdict vocative = UKRAINIAN.validate(pinned, "Алєксєю");
        final Verdict respelled = UKRAINIAN.validate(pinned, "Олексій");

        assertThat(vocative.rejections()).isEmpty();
        assertThat(respelled.rejections()).containsExactly(Reason.RESPELLING);
    }

    @Test
    void validate_obliqueEnding_isFlaggedNotRejected() {
        final Verdict instrumental = UKRAINIAN.validate(name("Valdor", TermType.CHARACTER), "Валдором");
        final Verdict vocative = UKRAINIAN.validate(name("Alexei", TermType.CHARACTER), "Алєксєю");
        final Verdict nominative = UKRAINIAN.validate(name("Valdor", TermType.CHARACTER), "Валдор");

        assertThat(instrumental.rejections()).isEmpty();
        assertThat(instrumental.flags()).containsExactly(Flag.OBLIQUE_ENDING);
        assertThat(instrumental.reasons()).containsExactly("not the dictionary form");
        assertThat(vocative.flags()).containsExactly(Flag.OBLIQUE_ENDING);
        assertThat(nominative.flags()).isEmpty();
        assertThat(nominative.passes()).isTrue();
    }

    @Test
    void validate_languageWithoutEndingKeys_neverFlagsAnEnding() {
        final Verdict verdict = validator("en", Map.of()).validate(name("Valdor", TermType.CHARACTER), "Valdorom");

        assertThat(verdict.flags()).isEmpty();
    }

    @Test
    void validate_stressMarkLeftInTheTarget_isRejected() {
        final Verdict verdict = UKRAINIAN.validate(name("Zotov", TermType.CHARACTER), "Зо\u0301тов");

        assertThat(verdict.rejections()).containsExactly(Reason.STRESS_MARK);
    }

    private static TargetValidator validator(final String target, final Map<String, String> conventions) {
        return TargetValidator.of(target, "en", NamePolicy.TRANSLITERATE, TargetContext.none(), conventions);
    }

    private static GlossaryEntry name(final String term, final TermType type) {
        return GlossaryEntry.of("p1:" + term, "p1", term, null, type, Gender.MALE, false);
    }
}
