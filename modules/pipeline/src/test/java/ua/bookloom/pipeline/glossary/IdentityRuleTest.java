package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.glossary.IdentityRule.Kept;

/**
 * The one identity rule (15i.N6): across scripts a rendering equal to the source is no translation and is refused,
 * unless the entry is a brand, an identifier or kept as written; in one script a copy is allowed. The name suggestion,
 * the term-mapping check and the recurring-term path (15i.N7) all apply it.
 */
class IdentityRuleTest {

    @ParameterizedTest(name = "{0} -> {1} ({2} to {3}, {4}): refused {5}")
    @CsvSource(
            delimiter = '|',
            value = {
                // a copy across scripts is refused, whatever its case or outer space
                "Arlo|Arlo|en|uk|NOT_KEPT|true",
                "Arlo|ARLO|en|uk|NOT_KEPT|true",
                "Arlo|' Arlo '|en|uk|NOT_KEPT|true",
                "Hale Street|Hale Street|en|uk|NOT_KEPT|true",
                "OTIS|OTIS|en|uk|NOT_KEPT|true",
                "Арло|Арло|uk|en|NOT_KEPT|true",
                // a rendering that differs is no copy
                "Arlo|Арло|en|uk|NOT_KEPT|false",
                "Arlo Venn|Arlo Венн|en|uk|NOT_KEPT|false",
                // the exceptions: a kept original and an identifier (a brand is none, 15j.J1)
                "KESSEL OPTIK|KESSEL OPTIK|en|uk|KEEP_ORIGINAL|false",
                "ZX-81|ZX-81|en|uk|NOT_KEPT|false",
                "A7|A7|en|uk|NOT_KEPT|false",
                "550e8400-e29b-41d4-a716-446655440000|550e8400-e29b-41d4-a716-446655440000|en|uk|NOT_KEPT|false",
                // one script on both sides: a copy can be right
                "Arlo|Arlo|en|de|NOT_KEPT|false",
                "Arlo|Arlo|en|fr|NOT_KEPT|false",
                "Арло|Арло|uk|ru|NOT_KEPT|false",
                // nothing to copy: no letter, or a script the code does not know
                "1984|1984|en|uk|NOT_KEPT|false",
                "Arlo|Arlo|en|xx|NOT_KEPT|false",
            })
    void refuses_byScriptsAndExceptions_decidesAsTheRuleSays(
            final String term,
            final String rendering,
            final String source,
            final String target,
            final Kept kept,
            final boolean expected) {
        assertThat(IdentityRule.refuses(term, rendering, source, target, kept)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZX-81", "A7", "R2-D2", "550e8400-e29b-41d4-a716-446655440000"})
    void isIdentifier_oneTokenWithLetterAndDigit_isTrue(final String term) {
        assertThat(IdentityRule.isIdentifier(term)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Arlo", "1984", "Arlo 7", "KESSEL OPTIK", "ZX 81", ""})
    void isIdentifier_wordNumberOrPhrase_isFalse(final String term) {
        assertThat(IdentityRule.isIdentifier(term)).isFalse();
    }
}
