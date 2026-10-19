package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.BookBrief;

/** The one answer to "what is the source language of this run": for a prompt, and for the code's own analysis. */
class SourceLanguageTest {

    private static Document declaring(final @Nullable String declared) {
        return new Document("d1", BookFormat.TXT, declared, null, null, null, "hash", Map.of(), List.of());
    }

    // IF a prompt were given the book's declared tag under Auto, THEN the model would be told a language nobody chose.
    @ParameterizedTest
    @CsvSource(
            value = {"fr,fr", "en-GB,en-GB", "uk,uk", "NULL,NULL"},
            nullValues = "NULL")
    void forPrompt_brief_isTheChosenTagOrNullForAuto(final String chosen, final String expected) {
        assertThat(SourceLanguage.forPrompt(BookBrief.defaults(chosen))).isEqualTo(expected);
    }

    // IF the code analysed a book with a language it could not know, THEN a French book would be read as English.
    @ParameterizedTest(name = "chosen={0} declared={1} -> {2}")
    @CsvSource(
            value = {
                // the person's choice wins over the book's own declaration
                "fr,en,fr",
                "uk,NULL,uk",
                // Auto: a declared tag the application recognises is used, in its normal form
                "NULL,en,en",
                "NULL,EN-gb,en",
                "NULL,ua,uk",
                "NULL,fr,fr",
                // Auto: a declaration that names nothing, or none, leaves the analysis without a language
                "NULL,xx,NULL",
                "NULL,und,NULL",
                "NULL,'',NULL",
                "NULL,NULL,NULL"
            },
            nullValues = "NULL")
    void forAnalysis_briefAndDocument_isTheChosenTagElseARecognisedDeclarationElseNull(
            final String chosen, final String declared, final String expected) {
        assertThat(SourceLanguage.forAnalysis(BookBrief.defaults(chosen), declaring(declared)))
                .isEqualTo(expected);
    }
}
