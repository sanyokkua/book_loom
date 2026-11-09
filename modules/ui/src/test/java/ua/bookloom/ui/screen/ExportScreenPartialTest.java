package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.state.RunState;

/**
 * An export is possible at any time except while a run translates: each reason it is not is said beside the action, a
 * partial book is announced.
 */
class ExportScreenPartialTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private void showWith(final ReviewCounts counts) throws TimeoutException {
        desk.willAnswerCounts(counts);
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
    }

    // IF the screen said nothing about a partial book, THEN a person would export half a book unaware.
    @Test
    void partial_idleRun_bannerSaysHowMuchIsTranslatedAndExportStaysAvailable() throws TimeoutException {
        showWith(counts(68));

        awaitFx(() -> isShown("export-banner-partial"));

        assertThat(textOf("export-banner-partial"))
                .contains("74 of 142 segments are translated. The other segments keep the original text");
        assertThat(button("export-run").isDisabled()).isFalse();
    }

    @Test
    void wholeBook_nothingPending_showsNoPartialBanner() throws TimeoutException {
        showWith(counts(0));

        assertThat(isShown("export-banner-partial")).isFalse();
    }

    // IF Export stayed pressable while a run translates, THEN the book written would change under the person.
    @Test
    void running_exportDisabledWithItsReasonBesideIt() throws TimeoutException {
        showWith(counts(68));

        publish(RunState.RUNNING);

        assertThat(button("export-run").isDisabled()).isTrue();
        assertThat(labelText("export-run-hint")).isEqualTo("Pause or stop the translation to export");
        assertThat(textOf("export-banner-running"))
                .contains("Pause or stop the translation to export.")
                .contains("You can export what is done so far; the rest keeps the original text.");
        assertThat(isShown("export-banner-partial")).isFalse();
    }

    // IF the banner's Pause were not wired, THEN it would tell the person to pause and give no way to.
    @Test
    void running_pausePressedOnTheBanner_asksTheRunningJobToPause() throws Exception {
        desk.willAnswerCounts(counts(68));
        openAndShowExport(EPUB);
        chooseModel();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        onFx(() -> shell.activate(ua.bookloom.ui.ViewNames.EXPORT));
        publish(RunState.RUNNING);

        onFx(((Button) required("export-banner-pause"))::fire);

        assertThat(job.calls().stream().filter("pause"::equals)).hasSize(1);
    }

    // IF an empty export were offered, THEN the person would write a book that is the original.
    @Test
    void nothingTranslated_exportDisabledWithItsReasonAndABanner() throws TimeoutException {
        showWith(new ReviewCounts(142, 0, 0, 0, 0, 142, 0, 0, 0));

        awaitFx(() -> button("export-run").isDisabled());

        assertThat(labelText("export-run-hint")).isEqualTo("Nothing is translated yet.");
        assertThat(textOf("export-banner-nothing"))
                .contains("Nothing is translated yet.")
                .contains("Start the translation first; you can export what is done at any pause.");
    }
}
