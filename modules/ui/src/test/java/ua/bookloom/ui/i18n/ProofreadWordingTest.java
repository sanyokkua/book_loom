package ua.bookloom.ui.i18n;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The per-chunk reviewer and the consistency pass are gone; no word a person reads names them. */
class ProofreadWordingTest {

    private static final Pattern REVIEWER = Pattern.compile("reviewer|рецензен", Pattern.CASE_INSENSITIVE);

    // The select ids (reviewer, reviewer_edit) are stored raiser ids; only the words shown to a person count.
    private static String shownWords(final String pattern) {
        final StringBuilder shown = new StringBuilder();
        int start = 0;
        int open = pattern.indexOf(" {", start);
        while (open >= 0) {
            int idStart = open;
            while (idStart > start && isIdChar(pattern.charAt(idStart - 1))) {
                idStart--;
            }
            shown.append(pattern, start, idStart).append('{');
            start = open + 2;
            open = pattern.indexOf(" {", start);
        }
        return shown.append(pattern, start, pattern.length()).toString();
    }

    private static boolean isIdChar(final char c) {
        return c == '_' || (c >= 'a' && c <= 'z');
    }

    // IF any shown words still said reviewer, THEN a person would meet a stage that no longer exists.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void everyMessage_eachLanguage_namesNoReviewer(final String language) {
        assertThat(Catalogues.load(language))
                .allSatisfy((key, pattern) ->
                        assertThat(shownWords(pattern)).as(key).doesNotContainPattern(REVIEWER));
    }

    // IF a refusal scope kept words for a call no prompt sends any more, THEN they would describe a removed stage.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void refusedScope_eachLanguage_hasNoWordsForARemovedCall(final String language) {
        assertThat(requireNonNull(Catalogues.load(language).get("settings.models.refused.scope")))
                .doesNotContain("reviewer {", "consistency {");
    }
}
