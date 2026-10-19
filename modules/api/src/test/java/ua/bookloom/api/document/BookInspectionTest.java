package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/**
 * {@code BookInspection}'s construction-time invariants.
 */
class BookInspectionTest {

    @Test
    void constructor_drmProtectedWithEncryptionScheme_keepsScheme() {
        final BookInspection inspection = new BookInspection(
                InspectionVerdict.DRM_PROTECTED,
                BookFormat.EPUB,
                null,
                "EPUB",
                "Adobe ADEPT",
                new LanguageEvidence(null, null, null, LanguageEvidence.Verdict.ABSENT));

        assertThat(inspection.encryptionScheme()).isEqualTo("Adobe ADEPT");
    }

    @SuppressWarnings("NullAway")
    @Test
    void constructor_nullLanguageEvidence_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new BookInspection(InspectionVerdict.READABLE, BookFormat.EPUB, null, "EPUB", null, null));
    }
}
