package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.document.LanguageEvidence.Verdict;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.ui.BookFixtures;

/** Which state an answered import becomes: chosen by the inspection's verdicts, never by an error code or a message. */
class ImportStateTest {

    static Stream<Arguments> warnings() {
        return Stream.of(
                Arguments.of(
                        "Witcher.epub",
                        BookFixtures.evidence("en", "en", "uk", Verdict.MISMATCH),
                        new LanguageWarning.Mismatch("en", "uk")),
                Arguments.of(
                        "odd.epub",
                        BookFixtures.evidence("xx-yy", null, "en", Verdict.UNRECOGNIZED),
                        new LanguageWarning.Unrecognized("xx-yy")),
                Arguments.of("latin.epub", BookFixtures.evidence("la", "la", "la", Verdict.MATCH), null),
                Arguments.of("plain.epub", BookFixtures.evidence(null, null, null, Verdict.ABSENT), null));
    }

    static Stream<Arguments> drm() {
        return Stream.of(
                Arguments.of("Purchased_Novel.epub", "EPUB", "Adobe ADEPT"),
                Arguments.of("Kobzar.fb2.zip", "FB2", "ZIP encryption"),
                Arguments.of("Mystery.epub", "EPUB", null));
    }

    static Stream<Arguments> unsupported() {
        return Stream.of(
                Arguments.of("book.pdf", "PDF"),
                Arguments.of("report.docx", "DOCX"),
                Arguments.of("mystery.bin", "Unknown"));
    }

    @Test
    void of_frankenstein_isDetectedWithTheCardTheInspectionGives() {
        final ImportState state = ImportStates.of("Frankenstein.epub", BookFixtures.frankensteinInspected());

        assertThat(state)
                .isEqualTo(new ImportState.Detected(
                        new BookCard(
                                "Frankenstein.epub",
                                BookFormat.EPUB,
                                "2.0",
                                "Frankenstein",
                                "Mary Shelley",
                                "en",
                                11,
                                78_214,
                                7,
                                0,
                                BookFixtures.pngCover()),
                        null));
    }

    @Test
    void of_plainTextWithNothingDeclared_isDetectedWithNoTitleLanguageOrCover() {
        final ImportedBook imported = BookFixtures.inspected(
                "notes",
                BookFormat.TXT,
                null,
                null,
                null,
                BookFixtures.evidence(null, null, null, Verdict.ABSENT),
                1,
                640,
                0,
                0,
                null);

        assertThat(ImportStates.of("notes.txt", imported))
                .isEqualTo(new ImportState.Detected(
                        new BookCard("notes.txt", BookFormat.TXT, null, null, null, null, 1, 640, 0, 0, null), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("warnings")
    void of_languageEvidence_choosesTheWarning(
            final String fileName, final LanguageEvidence evidence, final @Nullable LanguageWarning expected) {
        final ImportedBook imported =
                BookFixtures.inspected("p", BookFormat.EPUB, "3.0", "T", "A", evidence, 2, 10, 0, 0, null);

        assertThat(ImportStates.of(fileName, imported))
                .isInstanceOfSatisfying(
                        ImportState.Detected.class,
                        detected -> assertThat(detected.warning()).isEqualTo(expected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drm")
    void of_drmVerdict_isDrmBlockedWithTheSchemeWhenNamed(
            final String fileName, final String type, final @Nullable String scheme) {
        assertThat(ImportStates.of(fileName, BookFixtures.drmProtected(type, scheme)))
                .isEqualTo(new ImportState.DrmBlocked(fileName, scheme));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupported")
    void of_unsupportedVerdict_isUnsupportedNamingTheDetectedType(final String fileName, final String type) {
        assertThat(ImportStates.of(fileName, BookFixtures.refused(InspectionVerdict.UNSUPPORTED, type)))
                .isEqualTo(new ImportState.Unsupported(fileName, type));
    }

    @Test
    void of_readableBookWithNoProject_isRefusedWithValidation() {
        assertThat(ImportStates.of("broken.epub", BookFixtures.refused(InspectionVerdict.READABLE, "EPUB")))
                .isInstanceOfSatisfying(
                        ImportState.Refused.class,
                        refused -> assertThat(refused.error().code()).isEqualTo(ErrorCode.validation));
    }
}
