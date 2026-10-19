package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A mark that doubles as an apostrophe can never be a quote pair, or every apostrophe reads as a stray close. */
class QuoteConventionsTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "uk", "ru", "be", "bg", "sr", "pl", "cs", "sk", "sl", "hr", "de", "en", "fr", "es", "pt", "it", "ca",
                "ro", "hu", "nl", "tr", "el"
            })
    void ownLine_anyLanguage_usesNoApostropheAsAQuoteMark(final String language) {
        assertThat(QuoteConventions.ownLine(language).orElseThrow())
                .noneMatch(pair -> "’'‘".indexOf(pair.open()) >= 0 || "’'".indexOf(pair.close()) >= 0);
    }
}
