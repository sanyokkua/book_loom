package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ThemeTestSupport;

/** The summary card at the top of the Providers form: whose model, what the last check found, and its two buttons. */
class SettingsSummaryScreenTest extends SettingsScreenTestBase {

    private static Result<VerificationReport> allPassed() {
        return Result.ok(new VerificationReport(List.of(
                new StageOutcome(
                        VerificationStage.CONNECTION, StageStatus.PASSED, null, null, Duration.ofMillis(41), null),
                new StageOutcome(VerificationStage.MODELS, StageStatus.PASSED, null, null, Duration.ofMillis(5), 3),
                new StageOutcome(
                        VerificationStage.INFERENCE, StageStatus.PASSED, null, null, Duration.ofMillis(1200), null))));
    }

    // IF the card did not say whose model it names, THEN a person could not tell which program a translation uses.
    @Test
    void summary_currentProviderWithAModel_namesTheProviderAndTheModel() {
        openSettings();

        onFx(() -> viewModel().model().set("gemma3:12b"));

        assertThat(text("settings-summary-title")).isEqualTo("Your AI model: Ollama · gemma3:12b");
    }

    // IF the current provider's card followed unsaved typing, THEN it would name a model that translation does not use.
    @Test
    void summary_currentProviderModelTypedNotSaved_stillNamesTheSavedModel() {
        openSettings();
        onFx(() -> viewModel().model().set("gemma3:12b"));

        onFx(() -> viewModel().draftModel().set("qwen3:8b"));

        assertThat(text("settings-summary-title")).isEqualTo("Your AI model: Ollama · gemma3:12b");
    }

    // IF another provider's card ignored what is typed, THEN its model line could never show the choice being made.
    @Test
    void summary_otherProviderModelTyped_showsTheTypedModel() {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));

        onFx(() -> viewModel().draftModel().set("qwen3:8b"));

        assertThat(text("settings-summary-title")).isEqualTo("This provider's model: LM Studio · qwen3:8b");
    }

    @Test
    void summary_currentProviderWithoutAModel_saysNoModelIsChosenYet() {
        openSettings();

        assertThat(text("settings-summary-title")).isEqualTo("Your AI model: Ollama · no model chosen yet");
    }

    // IF a provider that is not in use claimed "Your AI model", THEN looking at it would seem to change the choice.
    @Test
    void summary_otherProviderOpened_saysItIsThatProvidersModel() {
        openSettings();

        onFx(() -> viewModel().editProvider("lmstudio"));

        assertThat(text("settings-summary-title")).isEqualTo("This provider's model: LM Studio · no model chosen yet");
    }

    @Test
    void summary_listingMade_countsTheModelsAvailable() throws TimeoutException {
        catalog.respondWith("gemma3:12b", "qwen3:8b");

        openSettingsSettled();

        assertThat(text("settings-summary-line")).isEqualTo("2 models available");
    }

    // IF a passed check were not shown on the card, THEN a person would have to open the details to know it works.
    @Test
    void summary_checkPassed_saysReadyAndHowFastItAnswers() throws TimeoutException {
        catalog.respondWith("gemma3:12b", "qwen3:8b");
        openSettingsSettled();
        onFx(() -> viewModel().model().set(MODEL));

        checkWithReport(allPassed());

        assertThat(text("settings-summary-title")).isEqualTo("Your AI model: Ollama · gemma3:12b — ready ✓");
        assertThat(text("settings-summary-line")).isEqualTo("Answers in about 1.2 s · 2 models available");
        assertThat(required("settings-summary").getStyleClass()).doesNotContain("summary-card-bad");
    }

    // IF a failed check left the card calm, THEN the one place a person looks first would say all is well.
    @Test
    void summary_checkFailed_saysNotAnsweringAndTurnsBad() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        assertThat(text("settings-summary-title")).endsWith("— not answering");
        assertThat(required("settings-summary").getStyleClass()).contains("summary-card-bad");
        assertThat(fold().isExpanded()).isTrue();
    }

    // IF the card's Test did not run the real model check, THEN it would be a button that looks busy and proves
    // nothing.
    @Test
    void summaryTest_modelChosen_runsTheInferenceCheck() throws InterruptedException {
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));

        onFx(((Button) required("settings-summary-test"))::fire);
        verifier.awaitEntered();

        assertThat(verifier.policies()).containsExactly(VerificationPolicy.FULL);
    }

    // IF Change model only opened the details, THEN the person would still have to find the field.
    @Test
    void changeModel_pressed_opensTheDetailsAndFocusesTheModelEntry() throws TimeoutException {
        openSettings();
        assertThat(fold().isExpanded()).isFalse();

        onFx(() -> button("settings-summary-change-model").fire());
        awaitFx(() -> modelCombo().getEditor().isFocused());

        assertThat(fold().isExpanded()).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> modelCombo().getEditor().isFocused()))
                .isTrue();
    }
}
