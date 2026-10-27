package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ProviderTest;

/**
 * The findings the settings screen draws for a check: the chips, the visible reason under a failed or qualified one,
 * and the in-place refusal of a whole check.
 */
class SettingsChipsScreenTest extends SettingsScreenTestBase {

    // IF a status shared a look with another, or leaned on colour alone, THEN a person could not tell a failed stage
    // from a passed one; each carries its own class, glyph and words.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "PASSED    | chip-ok      | ✓ Connection: Passed",
                "SOFT_PASS | chip-warn    | ! Connection: Passed with a note",
                "FAILED    | chip-err     | ✕ Ollama does not answer",
                "SKIPPED   | chip-neutral | – Connection: not attempted",
            })
    void chips_fourStatuses_renderDistinctly(final StageStatus status, final String styleClass, final String text)
            throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(status));

        final Labeled connection = result("CONNECTION");
        assertThat(connection.getStyleClass()).contains("chip", styleClass);
        assertThat(connection.getStyleClass())
                .filteredOn(name -> name.startsWith("chip-"))
                .containsExactly(styleClass);
        assertThat(connection.getText()).isEqualTo(text);
    }

    // IF the stages after a failed first one were drawn as passed or left out, THEN the report would misstate what was
    // attempted.
    @Test
    void chips_unreachableFirstStage_threeChipsInOrderLaterOnesNotAttempted() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        assertThat(checksGrid().lookupAll(".check-result"))
                .extracting(Node::getId)
                .containsExactly("check-result-CONNECTION", "check-result-MODELS", "check-result-INFERENCE");
        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        result("CONNECTION").getText(),
                        result("MODELS").getText(),
                        result("INFERENCE").getText())))
                .containsExactly("✕ Ollama does not answer", "– Models: not attempted", "– Inference: not attempted");
    }

    // IF a passed stage said only "passed", THEN a 1.2-second model and a 40-second one would look the same; each
    // result is a sentence that names what was measured.
    @Test
    void chips_allThreePassedWithMeasurements_showTheMeasuredValues() throws TimeoutException {
        openSettings();

        checkWithReport(Result.ok(new VerificationReport(List.of(
                new StageOutcome(
                        VerificationStage.CONNECTION, StageStatus.PASSED, null, null, Duration.ofMillis(41), null),
                new StageOutcome(VerificationStage.MODELS, StageStatus.PASSED, null, null, Duration.ofMillis(5), 3),
                new StageOutcome(
                        VerificationStage.INFERENCE, StageStatus.PASSED, null, null, Duration.ofMillis(1200), null)))));

        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        result("CONNECTION").getText(),
                        result("MODELS").getText(),
                        result("INFERENCE").getText())))
                .containsExactly(
                        "✓ Ollama answers (41 ms)",
                        "✓ 3 models found — the chosen one is there",
                        "✓ The model replied in 1.2 s");
    }

    // IF a failed stage's reason lived only in a tooltip, THEN a person who cannot hover would see a red chip and
    // never learn why. Ollama on this computer that does not answer is closed, so the reason says to open it.
    @Test
    void chipDetail_failedStageOfLocalOllama_showsThatTheAppIsNotOpenVisibly() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        final Label detail = detail("CONNECTION");
        assertThat(detail.getText()).isEqualTo("Ollama is not open. Open the app, then test again.");
        assertThat(detail.getStyleClass()).contains("hint");
        assertThat(detail.isVisible()).isTrue();
        assertThat(result("CONNECTION").getParent())
                .isSameAs(detail("CONNECTION").getParent());
    }

    // IF the failure's own English words were shown, THEN a Ukrainian screen would explain a failed stage in English.
    @Test
    void chipDetail_failedStageOfLocalOllamaInUkrainian_saysThatTheAppIsNotOpenInUkrainian() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        assertThat(detail("CONNECTION").getText())
                .isEqualTo("Ollama не відкрито. Відкрийте застосунок і перевірте ще раз.");
    }

    // IF the refusal kept its English words, THEN a Ukrainian screen would refuse a check in English.
    @Test
    void checkRefusal_validationRefusalInUkrainian_isWordedInUkrainian() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openSettings();
        final Label refusal = (Label) required("settings-check-refusal");
        verifier.respondWith(Result.err(AppError.of(ErrorCode.validation, "Unknown provider", "No such provider.")));

        onFx(() -> {
            viewModel().draftModel().set(MODEL);
            viewModel().tests().run(ProviderTest.INFERENCE);
        });
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(refusal::isVisible));

        assertThat(refusal.getText())
                .isEqualTo("Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.");
    }

    // IF a soft pass showed the discovery error's message instead of the note, THEN the real verifier's report, which
    // carries both, would read as a failure under a passing chip.
    @Test
    void chipDetail_softPassWithErrorAndNote_showsTheNote() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.SOFT_PASS));

        assertThat(detail("CONNECTION").getText()).isEqualTo("model list unavailable");
    }

    // IF a passed or skipped stage carried a detail line, THEN a person would look for a problem that is not there.
    @Test
    void chipDetail_passedAndSkippedStages_haveNoDetailLine() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.PASSED));

        assertThat(checksGrid().lookup("#chip-detail-CONNECTION")).isNull();
    }

    // IF a refusal the person can fix were not visible in place, THEN pressing Check would appear to do nothing.
    @Test
    void checkRefusal_validationRefusal_shownInPlaceAndHiddenAfterwards() throws TimeoutException {
        openSettings();
        final Label refusal = (Label) required("settings-check-refusal");
        assertThat(refusal.isVisible()).isFalse();
        assertThat(refusal.isManaged()).isFalse();
        verifier.respondWith(Result.err(AppError.of(ErrorCode.validation, "Unknown provider", "No such provider.")));

        onFx(() -> {
            viewModel().draftModel().set(MODEL);
            viewModel().tests().run(ProviderTest.INFERENCE);
        });
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(refusal::isVisible));

        assertThat(refusal.getText()).isEqualTo("No such provider.");
        assertThat(refusal.isManaged()).isTrue();
        assertThat(refusal.getStyleClass()).contains("hint");
        onFx(() -> viewModel().draftModel().set("another-model"));
        assertThat(refusal.isVisible()).isFalse();
    }

    // IF a row claimed a verdict before any check ran, THEN the screen would report something nobody asked for.
    @Test
    void results_beforeAnyCheck_everyRowSaysNotTestedYet() {
        openSettings();

        assertThat(ThemeTestSupport.onFx(() -> List.of(
                        result("CONNECTION").getText(),
                        result("MODELS").getText(),
                        result("INFERENCE").getText())))
                .containsExactly("Not tested yet", "Not tested yet", "Not tested yet");
    }

    // IF a failed stage were not painted with the error background role, THEN it would not match the reference
    // rendering (light block, -color-err-bg).
    @Test
    void chip_failedStage_backgroundIsErrBg() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        final Region failed = (Region) result("CONNECTION");
        assertThat(failed.getBackground()).isNotNull();
        ThemeTestSupport.assertSameColour(
                failed.getBackground().getFills().get(0).getFill(), "#f7e4df", "failed chip background");
    }

    // IF the checks stayed available on a form the rules refuse, THEN a click would call an address nobody can read;
    // the screen says what to fix instead.
    @Test
    void checksBlocked_formHoldsABadNumber_buttonsOffAndTheLineSaysWhy() {
        openSettings();
        final Label blocked = (Label) required("settings-checks-blocked");
        assertThat(blocked.isVisible()).isFalse();

        onFx(() -> form().edit(draft -> draft.withRequestSeconds("many")));

        assertThat(ThemeTestSupport.onFx(blocked::isVisible)).isTrue();
        assertThat(blocked.getText()).isEqualTo("Fix the marked fields to run a test.");
        assertThat(ThemeTestSupport.onFx(
                        () -> button("settings-test-connection").isDisabled()))
                .isTrue();
    }

    // IF the checks still described the saved provider, THEN the hint would promise what the buttons no longer do.
    @Test
    void checksHint_saysTheyUseTheFormAsTyped() {
        openSettings();

        assertThat(text("settings-checks-hint"))
                .isEqualTo("They use the values in this form, saved or not, and change nothing.");
        assertThat(ThemeTestSupport.onFx(() -> scene.getRoot().lookup("#settings-checks-unsaved")))
                .isNull();
    }

    // IF the model note were hidden while adding, THEN a new provider's tests would be off with no word why.
    @Test
    void needsModelNote_whileAdding_isShownToo() {
        openSettings();

        onFx(() -> button("settings-provider-add").fire());

        assertThat(ThemeTestSupport.onFx(
                        () -> required("settings-check-needs-model").isVisible()))
                .isTrue();
    }

    // IF a rename left the old name in the sentence, THEN "Ollama does not answer" would name a provider that is gone;
    // the results themselves stay, since a name changes nothing the server sees.
    @Test
    void results_providerRenamed_keepTheFindingWithTheNewName() throws TimeoutException {
        openSettings();
        checkWithReport(firstStageOnly(StageStatus.FAILED));

        onFx(() -> form().edit(draft -> draft.withName("Home")));

        assertThat(ThemeTestSupport.onFx(() -> result("CONNECTION").getText())).isEqualTo("✕ Home does not answer");
    }
}
