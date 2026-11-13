package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ExportOpenWork;
import ua.bookloom.api.pipeline.ProofreadTally;
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

    private void showWithWork(final ExportOpenWork work) throws TimeoutException {
        exportService().openWorkIs(work);
        showWith(counts(0));
        awaitFx(() -> exports().figures().get() != null);
    }

    // IF the screen stayed silent, THEN a person would think the Proofread stage had read the whole book.
    @Test
    void proofread_stoppedAt40Of120_banner() throws TimeoutException {
        proofread.tallyIs(new ProofreadTally(1, 120, 40, 41));

        showWith(counts(0));
        awaitFx(() -> isShown("export-banner-proofread"));

        assertThat(textOf("export-banner-proofread"))
                .contains("Proofread stopped at 40 of 120 windows — Continue on Translating, or export as it is.");
    }

    @Test
    void proofread_noTally_noBanner() throws TimeoutException {
        showWith(counts(0));

        assertThat(isShown("export-banner-proofread")).isFalse();
    }

    @Test
    void waitingFact_twoFacts_namesBoth() throws TimeoutException {
        showWithWork(new ExportOpenWork(0, 3, List.of("Велемир", "Mara Quill")));
        awaitFx(() -> isShown("export-banner-waiting-fact"));

        assertThat(textOf("export-banner-waiting-fact"))
                .contains("3 segments wait on a fact you have since given (gender of Велемир, gender of Mara Quill)."
                        + " Continue on Translating to apply it.");
    }

    @Test
    void waitingFact_none_noBanner() throws TimeoutException {
        showWithWork(ExportOpenWork.NONE);

        assertThat(isShown("export-banner-waiting-fact")).isFalse();
    }

    @ParameterizedTest(name = "{0} waiting in Ukrainian")
    @CsvSource({
        "1, 1 сегмент чекає на факт",
        "2, 2 сегменти чекають на факт",
        "5, 5 сегментів чекають на факт",
        "21, 21 сегмент чекає на факт",
    })
    void waitingFact_ukrainianCounts_useTheirPluralForm(final int count, final String start) throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));

        showWithWork(new ExportOpenWork(0, count, List.of("Велемир")));
        awaitFx(() -> isShown("export-banner-waiting-fact"));

        assertThat(textOf("export-banner-waiting-fact")).contains(start).contains("(рід Велемир)");
    }

    @Test
    void openMarks_four_pluralBanner() throws TimeoutException {
        showWithWork(new ExportOpenWork(4, 0, List.of()));
        awaitFx(() -> isShown("export-banner-open-marks"));

        assertThat(textOf("export-banner-open-marks"))
                .contains("4 segments still carry a proofread mark. They are written as they are; review them on the"
                        + " desk.");
    }

    @Test
    void openMarks_one_singularBanner() throws TimeoutException {
        showWithWork(new ExportOpenWork(1, 0, List.of()));
        awaitFx(() -> isShown("export-banner-open-marks"));

        assertThat(textOf("export-banner-open-marks"))
                .contains("1 segment still carries a proofread mark. It is written as it is; review it on the desk.");
    }

    @Test
    void openMarks_runRunning_noBanner() throws TimeoutException {
        showWithWork(new ExportOpenWork(4, 0, List.of()));
        awaitFx(() -> isShown("export-banner-open-marks"));

        publish(RunState.RUNNING);

        assertThat(isShown("export-banner-open-marks")).isFalse();
    }

    @ParameterizedTest(name = "{0} marks in Ukrainian")
    @CsvSource({
        "1, 1 сегмент досі має позначку вичитки",
        "2, 2 сегменти досі мають позначку вичитки",
        "5, 5 сегментів досі мають позначку вичитки",
        "21, 21 сегмент досі має позначку вичитки",
    })
    void openMarks_ukrainianCounts_useTheirPluralForm(final int count, final String start) throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));

        showWithWork(new ExportOpenWork(count, 0, List.of()));
        awaitFx(() -> isShown("export-banner-open-marks"));

        assertThat(textOf("export-banner-open-marks")).contains(start);
    }
}
