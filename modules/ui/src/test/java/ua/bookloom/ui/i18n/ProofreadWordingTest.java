package ua.bookloom.ui.i18n;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The per-chunk reviewer is gone; the words that explain an edit or its raiser name the Proofread stage. */
class ProofreadWordingTest {

    private static final Pattern REVIEWER = Pattern.compile("reviewer|рецензен", Pattern.CASE_INSENSITIVE);

    // IF the edit tip or the raiser words still said reviewer, THEN a person would meet a stage that no longer exists.
    @ParameterizedTest
    @CsvSource({"en,review.edit.applied.tip", "uk,review.edit.applied.tip", "en,review.raiser", "uk,review.raiser"})
    void editAndRaiserWords_eachLanguage_doNotNameAReviewer(final String language, final String key) {
        // The select ids (reviewer, reviewer_edit) are stored raiser ids; only the words shown to a person count.
        final String shown = requireNonNull(Catalogues.load(language).get(key)).replaceAll("[a-z_]+ \\{", "{");

        assertThat(shown).doesNotContainPattern(REVIEWER);
    }
}
