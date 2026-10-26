package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.WaitingCall;

/**
 * Names &amp; style while a translation is at work: a banner says changes apply to the next segments translated, and
 * both tables stay editable, because what a run translates next reads the glossary as it is then.
 */
class NamesStyleRunBannerScreenTest extends TranslatingScreenTestBase {

    private static final String BANNER = "Translating is going on. Changes apply to the next segments translated.";
    private static final GlossaryEntry VELK =
            new GlossaryEntry("e1", "p1", "Velk", null, TermType.CHARACTER, Gender.UNKNOWN, false);

    // Brings the run into one of the states in which a translation is at work, then opens the screen.
    private void atWork(final String state) throws Exception {
        readyToStart();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.RUNNING);
        switch (state) {
            case "PAUSING" -> publish(RunState.PAUSING);
            case "STOPPING" -> publish(RunState.STOPPING);
            case "STUCK" -> stuck();
            case "WAITING" -> waiting();
            default -> {
                // RUNNING is the state already published.
            }
        }
        glossary.willAnswer(Result.ok(List.of(VELK)));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> !table().getItems().isEmpty());
    }

    private void stuck() {
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

    private void waiting() {
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

    private javafx.scene.control.TableView<?> table() {
        return (javafx.scene.control.TableView<?>) required("names-style-table");
    }

    private double minY(final String id) {
        return ThemeTestSupport.onFx(
                () -> required(id).localToScene(required(id).getBoundsInLocal()).getMinY());
    }

    private boolean isBannerShown() {
        final Node banner = optional("names-style-run-banner");
        return ThemeTestSupport.onFx(() -> banner != null && banner.isVisible() && banner.isManaged());
    }

    // IF the banner were missing, THEN a person editing a name mid-run would not know it reaches only later segments.
    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "PAUSING", "STOPPING", "STUCK", "WAITING"})
    void runBanner_translationAtWork_showsTheBannerWithItsWords(final String state) throws Exception {
        atWork(state);

        assertThat(isBannerShown()).as(state).isTrue();
        assertThat(textsUnder(required("names-style-run-banner"))).contains(BANNER);
    }

    // IF a paused run still showed the banner, THEN the words would promise a translation that is not going on.
    @ParameterizedTest
    @ValueSource(strings = {"PAUSED", "STOPPED", "COMPLETED"})
    void runBanner_runNotAtWork_isHidden(final String state) throws Exception {
        readyToStart();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.valueOf(state));
        glossary.willAnswer(Result.ok(List.of(VELK)));

        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        awaitFx(() -> !table().getItems().isEmpty());

        assertThat(isBannerShown()).as(state).isFalse();
    }

    // IF the tables were read-only while translating, THEN a wrong name could not be corrected as the run goes.
    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "PAUSING", "STOPPING", "STUCK", "WAITING"})
    void runBanner_translationAtWork_keepsTheTablesEditable(final String state) throws Exception {
        atWork(state);
        glossary.willAnswer(Result.ok(VELK.withTarget("Велк")));
        final TextField field = (TextField) ThemeTestSupport.onFx(() -> table().lookup(".text-field"));

        onFx(() -> {
            field.setText("Велк");
            field.fireEvent(new javafx.event.ActionEvent());
        });

        awaitFx(() -> !glossary.updated().isEmpty());
        assertThat(glossary.updated()).extracting(GlossaryEntry::target).containsExactly("Велк");
        assertThat(ThemeTestSupport.onFx(field::isDisabled)).isFalse();
    }

    @Test
    void runBanner_translationAtWork_sitsAboveBothCards() throws Exception {
        atWork("RUNNING");

        assertThat(minY("names-style-run-banner")).isLessThan(minY("names-style-card"));
    }
}
