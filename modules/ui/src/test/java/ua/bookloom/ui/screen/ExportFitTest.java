package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The Export screen of a partly translated book, in English and Ukrainian: the tiles, the buttons and every line that
 * says what will be written in the source language, to review or kept as is are whole, and so is the line naming a
 * segment written in its source after the export.
 */
class ExportFitTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    // IF a long Ukrainian line about untranslated segments were cut, THEN the person would not know what is written.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 944, 600", "uk, 944, 600", "en, 1920, 700", "uk, 1920, 700"})
    void screen_partialBookWithEveryLine_cutsNothing(final String language, final double width, final double height)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        desk.willAnswerCounts(new ReviewCounts(142, 50, 0, 6, 6, 30, 2, 3, 4));
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
        resizeScene(width, height);
        exportService()
                .reportWith(new ExportReport(
                        Path.of("Frankenstein.uk.epub"),
                        9,
                        1,
                        0,
                        0,
                        9,
                        0,
                        List.of(),
                        10,
                        ConsistencySummary.NOT_RUN,
                        0,
                        List.of(new SourceFallback("part0009.html:1", "ch12 · p02"))));
        onFx(() -> button("export-run").fire());
        awaitFx(() -> exports().outcome().get() != null);

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));

        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
    }
}
