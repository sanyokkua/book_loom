package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;

/** The invariants the import states enforce on themselves. */
class ImportStateTest {

    // IF a mismatch could be built for a book that declares no language, THEN the screen would name a language that
    // is not there in a warning about a disagreement that cannot exist.
    @Test
    void languageMismatch_cardWithoutDeclaredLanguage_isRejected() {
        final BookCard undeclared = new BookCard("notes.txt", BookFormat.TXT, null, null, null, 1, 1);

        assertThatNullPointerException().isThrownBy(() -> new ImportState.LanguageMismatch(undeclared, "uk"));
    }

    @Test
    void languageMismatch_cardWithDeclaredLanguage_exposesIt() {
        final BookCard declared = new BookCard("a.epub", BookFormat.EPUB, null, null, "en", 1, 1);

        assertThat(new ImportState.LanguageMismatch(declared, "uk").declaredLang())
                .isEqualTo("en");
    }
}
