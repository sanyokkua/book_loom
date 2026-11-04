package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.state.RunState;

/** Before Export book, the screen says how many segments will be written in the source language. */
class ExportBannersTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private static ReviewCounts fallback(final int count) {
        return new ReviewCounts(142, 50, 0, count, 6, 0, 0, count, 0, 0, count);
    }

    private void showWith(final ReviewCounts counts) throws TimeoutException {
        desk.willAnswerCounts(counts);
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
    }

    // IF the screen stayed silent, THEN a person would export a book with English paragraphs in it unaware.
    @Test
    void sourceFallback_twoSegments_pluralBanner() throws TimeoutException {
        showWith(fallback(2));

        awaitFx(() -> isShown("export-banner-source-fallback"));

        assertThat(textOf("export-banner-source-fallback"))
                .contains("2 segments will be written in the source language because their formatting marks could not"
                        + " be kept. Fix them on the review desk.");
    }

    @Test
    void sourceFallback_oneSegment_singularBanner() throws TimeoutException {
        showWith(fallback(1));

        awaitFx(() -> isShown("export-banner-source-fallback"));

        assertThat(textOf("export-banner-source-fallback"))
                .contains("1 segment will be written in the source language because its formatting marks could not"
                        + " be kept. Fix it on the review desk.");
    }

    @Test
    void sourceFallback_none_noBanner() throws TimeoutException {
        showWith(fallback(0));

        assertThat(isShown("export-banner-source-fallback")).isFalse();
    }

    // IF the banner stayed while a run translates, THEN it would state a count that is still changing.
    @Test
    void sourceFallback_runRunning_noBanner() throws TimeoutException {
        showWith(fallback(2));
        awaitFx(() -> isShown("export-banner-source-fallback"));

        publish(RunState.RUNNING);

        assertThat(isShown("export-banner-source-fallback")).isFalse();
    }

    @ParameterizedTest(name = "{0} segments in Ukrainian")
    @CsvSource({
        "1, 1 сегмент буде записано мовою оригіналу",
        "2, 2 сегменти буде записано мовою оригіналу",
        "5, 5 сегментів буде записано мовою оригіналу",
        "21, 21 сегмент буде записано мовою оригіналу",
    })
    void sourceFallback_ukrainianCounts_useTheirPluralForm(final int count, final String start)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));

        showWith(fallback(count));
        awaitFx(() -> isShown("export-banner-source-fallback"));

        assertThat(textOf("export-banner-source-fallback")).contains(start);
    }
}
