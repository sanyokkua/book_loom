package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.RunState;

/**
 * An export is possible at any time except while a run translates: each reason it is not is said beside the action, a
 * partial book is announced, and the last check, which needs the whole book, is switched off without touching the
 * person's saved choice.
 */
class ExportScreenPartialTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");
    private static final String STORED_KEY = "automation.lastCheck";

    private void showWith(final ReviewCounts counts) throws TimeoutException {
        desk.willAnswerCounts(counts);
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
    }

    private ToggleSwitch lastCheck() {
        return (ToggleSwitch) required("export-aux-consistency");
    }

    private @org.jspecify.annotations.Nullable String storedChoice() {
        return java.util.Objects.requireNonNull(stored.all().data(), "settings").get(STORED_KEY);
    }

    private void refreshCounts(final ReviewCounts counts) {
        desk.willAnswerCounts(counts);
        onFx(() -> exports().refreshStatement());
    }

    // IF the last check stayed on for half a book, THEN it would reread paragraphs that do not exist yet.
    @Test
    void partial_lastCheckOffAndDisabled_savedChoiceUntouched() throws TimeoutException {
        stored.put(AppSetting.LAST_CHECK, true);

        showWith(counts(68));
        awaitFx(() -> !lastCheck().isSelected());

        assertThat(lastCheck().isDisabled()).isTrue();
        assertThat(labelText("export-consistency-note")).isEqualTo("Runs when the whole book is translated");
        assertThat(storedChoice()).isEqualTo("true");
    }

    // IF the lock overwrote the setting, THEN finishing the book would not bring the person's choice back.
    @Test
    void partial_bookFinishesLater_lastCheckIsOnAgainAndTheSettingStillTrue() throws TimeoutException {
        stored.put(AppSetting.LAST_CHECK, true);
        showWith(counts(68));
        awaitFx(() -> !lastCheck().isSelected());

        refreshCounts(counts(0));
        awaitFx(() -> lastCheck().isSelected());

        assertThat(lastCheck().isDisabled()).isFalse();
        assertThat(storedChoice()).isEqualTo("true");
    }

    // The stored choice wins; with none stored the switch starts from the dial, on at Max.
    @ParameterizedTest
    @CsvSource(
            nullValues = "-",
            value = {"-, MAX, true", "false, MAX, false", "true, BALANCED, true", "-, BALANCED, false"})
    void lastCheck_wholeBook_startsFromTheStoredChoiceElseTheDial(
            final Boolean choice, final QualityDial dial, final boolean expected) throws TimeoutException {
        store(choice);
        showWith(counts(0));

        onFx(() -> injector.getInstance(BookBriefViewModel.class).setDial(dial));

        awaitFx(() -> lastCheck().isSelected() == expected);
        assertThat(lastCheck().isDisabled()).isFalse();
    }

    private void store(final Boolean choice) {
        if (choice != null) {
            stored.put(AppSetting.LAST_CHECK, choice);
        }
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

        onFx(() -> ((Button) required("export-banner-pause")).fire());

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
