package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ImportVerdict.Kind;

/**
 * The one line the import screen says per state, built without a scene: its kind and its exact words. Reading is
 * indeterminate and says only "Reading…", because the parsers report no chapters while they work.
 */
class ImportVerdictsTest {

    private static final Messages ENGLISH = new Messages((LocaleProvider) () -> Locale.ENGLISH);

    private static BookCard card(final int chapters, final int words) {
        return new BookCard("book.epub", BookFormat.EPUB, "3.0", "Title", "Author", "en", chapters, words, null);
    }

    static Stream<Arguments> detectedStates() {
        return Stream.of(
                Arguments.of(new ImportState.Opening("book.epub"), Kind.READING, "Reading…"),
                Arguments.of(
                        new ImportState.Detected(card(5, 4_149), null),
                        Kind.READY,
                        "BookLoom can translate this book: 5 chapters, about 4,100 words. Your file is never changed."),
                Arguments.of(
                        new ImportState.Detected(card(1, 640), null),
                        Kind.READY,
                        "BookLoom can translate this book: 1 chapter, 640 words. Your file is never changed."),
                Arguments.of(
                        new ImportState.Detected(card(5, 4_149), new LanguageWarning.Mismatch("en", "uk")),
                        Kind.TWO_MARKS,
                        "The file names two languages: its details say English, its chapters are marked Ukrainian."
                                + " BookLoom does not read the text to decide — check “Translate from” in the Book"
                                + " Brief."),
                Arguments.of(
                        new ImportState.Detected(card(5, 4_149), new LanguageWarning.Unrecognized("xx-yy")),
                        Kind.UNRECOGNIZED,
                        "The file declares “xx-yy”, which the application does not recognize. The model will"
                                + " identify the source language; you can choose it on the Book Brief."));
    }

    static Stream<Arguments> refusedStates() {
        return Stream.of(
                Arguments.of(
                        new ImportState.DrmBlocked("Purchased_Novel.epub", "Adobe ADEPT"),
                        Kind.DRM,
                        "Purchased_Novel.epub is locked by the shop it came from. Encryption: Adobe ADEPT."),
                Arguments.of(
                        new ImportState.DrmBlocked("Purchased_Novel.epub", null),
                        Kind.DRM,
                        "Purchased_Novel.epub is locked by the shop it came from."),
                Arguments.of(
                        new ImportState.Unsupported("book.pdf", "PDF"),
                        Kind.UNSUPPORTED,
                        "book.pdf is PDF, which is not supported. It reads EPUB, FB2, Markdown and plain text."),
                Arguments.of(
                        new ImportState.Refused(
                                "broken.epub",
                                AppError.of(ErrorCode.validation, "This file could not be opened", "Damaged.")),
                        Kind.DAMAGED,
                        "broken.epub could not be read as an EPUB. The file may be damaged."),
                Arguments.of(
                        new ImportState.AlreadyInProjects(
                                "earth.epub",
                                Path.of("/books/earth.epub"),
                                List.of("Earth Gravity · EN → UK", "Earth Gravity · EN → PL"),
                                "Earth Gravity"),
                        Kind.ALREADY,
                        "Already in projects: Earth Gravity · EN → UK, Earth Gravity · EN → PL."));
    }

    // A damaged file names the format its name promised; an unknown type and an empty book are never named "Unknown".
    static Stream<Arguments> damagedAndEmptyStates() {
        return Stream.of(
                Arguments.of(
                        new ImportState.Refused(
                                "odd.fb2",
                                AppError.of(ErrorCode.validation, "This file could not be opened", "Damaged.")),
                        Kind.DAMAGED,
                        "odd.fb2 could not be read as an FB2 book. The file may be damaged."),
                Arguments.of(
                        new ImportState.Refused(
                                "notes.md", AppError.of(ErrorCode.validation, "Not opened", "Damaged.")),
                        Kind.DAMAGED,
                        "notes.md could not be read as a Markdown file. The file may be damaged."),
                Arguments.of(
                        new ImportState.Refused(
                                "notes.txt", AppError.of(ErrorCode.validation, "Not opened", "Damaged.")),
                        Kind.DAMAGED,
                        "notes.txt could not be read as a plain text file. The file may be damaged."),
                Arguments.of(
                        new ImportState.Refused("book", AppError.of(ErrorCode.validation, "Not opened", "Damaged.")),
                        Kind.DAMAGED,
                        "book could not be read. Try downloading it again."),
                Arguments.of(
                        new ImportState.Unsupported("notes.xyz", null),
                        Kind.UNSUPPORTED,
                        "notes.xyz is not a format BookLoom reads. It reads EPUB, FB2, Markdown and plain text."),
                Arguments.of(new ImportState.NoText("empty.txt"), Kind.NO_TEXT, "This book has no text to translate."));
    }

    // IF a state said two lines, or the wrong one, THEN the person would not know in one glance what to do next.
    @ParameterizedTest
    @MethodSource({"detectedStates", "refusedStates", "damagedAndEmptyStates"})
    void of_eachState_isOneLineOfTheRightKindAndWords(final ImportState state, final Kind kind, final String text) {
        assertThat(ImportVerdicts.of(ENGLISH, state)).hasValueSatisfying(verdict -> {
            assertThat(verdict.kind()).isEqualTo(kind);
            assertThat(verdict.text()).isEqualTo(text);
        });
    }

    static Stream<Arguments> ukrainianStates() {
        return Stream.of(
                Arguments.of(
                        new ImportState.Refused(
                                "corrupt.epub", AppError.of(ErrorCode.validation, "Not opened", "Damaged.")),
                        "Не вдалося прочитати corrupt.epub як EPUB. Можливо, файл пошкоджено."),
                Arguments.of(
                        new ImportState.Unsupported("notes.xyz", null),
                        "Формат notes.xyz не підтримується. Програма читає EPUB, FB2, Markdown і простий текст."),
                Arguments.of(new ImportState.NoText("empty.txt"), "У цій книзі немає тексту для перекладу."));
    }

    // IF the Ukrainian words were missing or fell back to English, THEN a Ukrainian window would mix languages.
    @ParameterizedTest
    @MethodSource("ukrainianStates")
    void of_refusalsUnderUkrainian_areWordedInUkrainian(final ImportState state, final String text) {
        final Messages ukrainian = new Messages((LocaleProvider) () -> Locale.forLanguageTag("uk"));
        assertThat(ImportVerdicts.of(ukrainian, state))
                .hasValueSatisfying(verdict -> assertThat(verdict.text()).isEqualTo(text));
    }

    @Test
    void of_idle_hasNoVerdictLine() {
        assertThat(ImportVerdicts.of(ENGLISH, new ImportState.Idle())).isEmpty();
    }
}
