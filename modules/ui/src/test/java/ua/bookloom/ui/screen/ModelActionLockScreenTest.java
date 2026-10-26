package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.WaitingCall;

/**
 * Model actions wait while a translation is at work (app-shell "Disable model actions while a translation is at
 * work"): in Translating, Pausing, Stuck call, Stopping and Waiting for the AI model, Prepare names, Scan, Review,
 * Translate and Fill in from the book are off and say why; in every paused or ended state they are free again.
 */
class ModelActionLockScreenTest extends TranslatingScreenTestBase {

    private static final String PAUSE_FIRST =
            "Pause the translation first — BookLoom sends one model request at a time";
    private static final List<String> NAMES_ACTIONS =
            List.of("names-style-auto", "names-style-scan", "names-style-review", "names-style-translate");

    private void runIn(final RunState state) throws Exception {
        readyToStart();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(state);
    }

    private void waitForTheProgram() {
        mirror().publishRunState(RunState.PAUSED);
        mirror().review()
                .publishProviderError(AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing is listening."));
        mirror().review()
                .publishRecovery(new RecoveryState(
                        RecoveryWaiting.Status.WAITING,
                        1,
                        LocalTime.of(2, 14),
                        Instant.parse("2026-10-02T03:00:00Z"),
                        ErrorCode.unreachable,
                        12));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void stuckOnAJudge() {
        mirror().live()
                .publishWaitingCall(new WaitingCall(
                        CallKind.REVIEW,
                        "ch7 · p42",
                        0,
                        1,
                        2,
                        Duration.ofSeconds(61),
                        Duration.ofSeconds(90),
                        Duration.ofSeconds(61),
                        true));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private List<String> disabledNamesActions() {
        return NAMES_ACTIONS.stream().filter(id -> button(id).isDisabled()).toList();
    }

    // IF a model action ran beside a translation, THEN two requests would race for a model that answers one at a time.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"RUNNING", "PAUSING", "STOPPING"})
    void namesActions_translationAtWork_areOffAndSayPauseFirst(final RunState state) throws Exception {
        runIn(state);

        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(disabledNamesActions()).isEqualTo(NAMES_ACTIONS);
        assertThat(Tips.explanationOf(button("names-style-auto"))).contains(PAUSE_FIRST);
        assertThat(Tips.explanationOf(button("names-style-scan"))).contains(PAUSE_FIRST);
    }

    // IF a stuck call freed the model actions, THEN a second request would queue behind the one that hangs.
    @Test
    void namesActions_stuckCall_areOffAndSayPauseFirst() throws Exception {
        runIn(RunState.RUNNING);
        stuckOnAJudge();

        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(disabledNamesActions()).isEqualTo(NAMES_ACTIONS);
        assertThat(Tips.explanationOf(button("names-style-auto"))).contains(PAUSE_FIRST);
    }

    // IF waiting for the model freed the actions, THEN the person could start a pass the model cannot answer.
    @Test
    void namesActions_waitingForTheAiModel_areOffAndSayPauseFirst() throws Exception {
        runIn(RunState.RUNNING);
        waitForTheProgram();

        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(disabledNamesActions()).isEqualTo(NAMES_ACTIONS);
        assertThat(Tips.explanationOf(button("names-style-scan"))).contains(PAUSE_FIRST);
    }

    @Test
    void fillInFromTheBook_waitingForTheAiModel_isOffAndSaysPauseFirst() throws Exception {
        runIn(RunState.RUNNING);
        waitForTheProgram();

        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));

        assertThat(button("brief-suggest-style").isDisabled()).isTrue();
        assertThat(Tips.explanationOf(button("brief-suggest-style"))).contains(PAUSE_FIRST);
    }

    // IF the reason were only in the tooltip, THEN a disabled button, which shows none, would never say why it is off.
    @Test
    void fillInFromTheBook_translationAtWork_saysWhyInAVisibleLine() throws Exception {
        runIn(RunState.RUNNING);

        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));

        final javafx.scene.control.Label reason = (javafx.scene.control.Label) required("brief-suggest-reason");
        assertThat(reason.isVisible()).isTrue();
        assertThat(reason.getText()).contains(PAUSE_FIRST);
    }

    // IF a paused or ended run still held the model actions, THEN the person could not fix the glossary while it waits.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"PAUSED", "STOPPED", "COMPLETED", "FAILED"})
    void namesActions_runPausedOrEnded_areFree(final RunState state) throws Exception {
        runIn(state);

        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(disabledNamesActions()).isEmpty();
        assertThat(Tips.explanationOf(button("names-style-auto")))
                .isPresent()
                .get()
                .asString()
                .doesNotContain(PAUSE_FIRST);
    }

    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"PAUSED", "STOPPED"})
    void fillInFromTheBook_runPausedOrStopped_isFree(final RunState state) throws Exception {
        runIn(state);

        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));

        assertThat(button("brief-suggest-style").isDisabled()).isFalse();
        assertThat(required("brief-suggest-reason").isVisible()).isFalse();
    }

    // IF Start began a run beside the hidden names pass, THEN both would race for the model; Start waits and says so.
    @Test
    void start_namesPassHidden_isOffAndSaysWaitUntilTheNamesPassEnds() throws Exception {
        readyToStart();
        onFx(() -> {
            final ActivityTracker tracker = injector.getInstance(ActivityTracker.class);
            tracker.hide(tracker.begin(ActivityKind.NAMES_PASS, () -> {}).id());
        });

        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(button("names-style-start").isDisabled()).isTrue();
        assertThat(labelText("names-style-start-hint")).isEqualTo("Wait until the names pass ends");
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        assertThat(button("translating-start").isDisabled()).isTrue();
        assertThat(Tips.explanationOf(button("translating-start"))).contains("Wait until the names pass ends");
    }

    // IF a pass still in the foreground did not hold Start, THEN a run would begin beside the names pass.
    @Test
    void start_namesPassVisible_isOffAndSaysWaitUntilTheNamesPassEnds() throws Exception {
        readyToStart();
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.NAMES_PASS, () -> {}));

        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        assertThat(button("names-style-start").isDisabled()).isTrue();
        assertThat(labelText("names-style-start-hint")).isEqualTo("Wait until the names pass ends");
    }
}
