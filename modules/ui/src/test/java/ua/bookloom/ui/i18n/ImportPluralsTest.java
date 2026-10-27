package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The import page's counted phrases take the right plural form in both languages. */
class ImportPluralsTest {

    private static Messages messages(final String language) {
        return new Messages((LocaleProvider) () -> Locale.forLanguageTag(language));
    }

    // IF the Ukrainian chapter count used one form for every number, THEN "5 розділ" would read as a mistake.
    @ParameterizedTest
    @CsvSource({"1, 1 розділ", "2, 2 розділи", "5, 5 розділів", "11, 11 розділів", "21, 21 розділ", "22, 22 розділи"})
    void verdictReady_ukrainian_chaptersTakeTheirPluralForm(final int chapters, final String expected) {
        assertThat(messages("uk").get(MessageKey.IMPORT_VERDICT_READY, chapters, "640 слів"))
                .isEqualTo("BookLoom може перекласти цю книгу: " + expected
                        + ", 640 слів. Ваш файл ніколи не змінюється.");
    }

    @ParameterizedTest
    @CsvSource({"1, 1 chapter", "2, 2 chapters", "11, 11 chapters"})
    void verdictReady_english_chaptersTakeOneOrOther(final int chapters, final String expected) {
        assertThat(messages("en").get(MessageKey.IMPORT_VERDICT_READY, chapters, "640 words"))
                .isEqualTo(
                        "BookLoom can translate this book: " + expected + ", 640 words. Your file is never changed.");
    }

    @ParameterizedTest
    @CsvSource({"1, 1 зображення", "3, 3 зображення", "5, 5 зображень", "21, 21 зображення"})
    void aboutSummary_ukrainian_imagesTakeTheirPluralForm(final int images, final String expected) {
        assertThat(messages("uk").get(MessageKey.IMPORT_ABOUT_SUMMARY, "EPUB", 2, "640 слів", images, "обкладинка"))
                .isEqualTo("EPUB · 2 розділи · 640 слів · " + expected + " · обкладинка");
    }

    @ParameterizedTest
    @CsvSource({"1, 1 image", "3, 3 images"})
    void aboutSummary_english_imagesTakeOneOrOther(final int images, final String expected) {
        assertThat(messages("en").get(MessageKey.IMPORT_ABOUT_SUMMARY, "EPUB", 1, "640 words", images, "No cover"))
                .isEqualTo("EPUB · 1 chapter · 640 words · " + expected + " · No cover");
    }
}
