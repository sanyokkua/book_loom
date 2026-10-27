package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.TechnicalDetails;

/** The Models tab as the window builds it: the chooser, what the program says, and the three settings. */
class SettingsModelsScreenTest extends SettingsModelsScreenTestBase {

    // IF the tab stayed disabled or listed one provider only, THEN a model of the other could not be set up.
    @Test
    void tab_opened_listsTheModelsOfEveryProvider() throws TimeoutException {
        openModels();

        assertThat(modelsTab().isDisable()).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getItems().stream()
                        .map(ModelEntry::providerName)
                        .map(String::valueOf)
                        .toList()))
                .containsExactly("Ollama", "Ollama", "LM Studio");
        assertThat(ThemeTestSupport.onFx(() ->
                        modelsCombo().getConverter().toString(modelsCombo().getValue())))
                .isEqualTo("gemma4:e4b · Ollama");
    }

    // IF the tip stayed on the Providers tab only, THEN a person on the Models tab would see no way to reload.
    @Test
    void refresh_pressed_readsTheProvidersAgain() throws TimeoutException {
        openModels();
        final int before = catalog.callCount();

        press("models-refresh");
        awaitFx(() -> !roster().listing().get());

        assertThat(catalog.callCount()).isEqualTo(before + 2);
    }

    // IF a provider that did not answer was left unsaid, THEN Refresh would seem to do nothing while Ollama is down.
    @Test
    void refresh_ollamaDoesNotAnswer_saysSoUnderTheChooser() throws TimeoutException {
        openModels();
        assertThat(visible("models-unanswered")).isFalse();
        catalog.failFor("ollama", AppError.of(ErrorCode.unreachable, "Unreachable", "No answer."));

        press("models-refresh");
        awaitFx(() -> !roster().listing().get());

        assertThat(visible("models-unanswered")).isTrue();
        assertThat(shown("models-unanswered"))
                .isEqualTo("Ollama did not answer, so only its models BookLoom already knows are shown.");
    }

    // IF only the context length were shown, THEN the loaded figure that suggests the window would be invisible.
    @Test
    void found_ollamaReportsBothFigures_showsReadableAndLoaded() throws TimeoutException {
        capabilities.report("ollama", GEMMA, 131072, 16384);

        openModels();

        assertThat(shown("models-found-text"))
                .contains("131,072 tokens")
                .contains("16,384 loaded now")
                .contains("structured answers: not checked yet");
    }

    // IF "not checked yet" named nothing to do, THEN a person could not learn how to get the answer.
    @Test
    void found_noInferenceTestThisSession_saysNotCheckedYetAndNamesTheTest() throws TimeoutException {
        openModels();

        assertThat(shown("models-found-text"))
                .contains("not checked yet")
                .contains("Test inference on the Providers tab");
    }

    // IF the Ukrainian hint named a label the Providers tab does not show, THEN the person could not find the test.
    @Test
    void found_ukrainianNoInferenceTest_namesTheUkrainianTestLabel() throws TimeoutException {
        useLocale(Locale.of("uk"));

        openModels();

        assertThat(shown("models-found-text"))
                .contains(
                        "\u00ab\u041f\u0435\u0440\u0435\u0432\u0456\u0440\u0438\u0442\u0438 \u0432\u0456\u0434\u043f\u043e\u0432\u0456\u0434\u044c\u00bb");
    }

    // IF a stored value were not shown, THEN Save would overwrite it with defaults.
    @Test
    void temperature_storedMyValue_showsValueSliderAndHint() throws TimeoutException {
        modelSettings.put(with("ollama", GEMMA, TemperatureChoice.VALUE, 0.5, null, null));

        openModels();

        assertThat(ThemeTestSupport.onFx(() -> ((ToggleButton) required("models-temperature-value")).isSelected()))
                .isTrue();
        assertThat(ThemeTestSupport.onFx(() -> ((Slider) required("models-temperature-slider")).getValue()))
                .isEqualTo(0.5);
        assertThat(shown("models-temperature-hint")).isEqualTo("Every request uses this value.");
        assertThat(shown("models-temperature-readout")).isEqualTo("0.50");
    }

    @Test
    void temperature_storedDontSend_saysNotSentAtAll() throws TimeoutException {
        modelSettings.put(with("ollama", GEMMA, TemperatureChoice.OMIT, null, null, null));

        openModels();

        assertThat(shown("models-temperature-hint")).startsWith("Not sent at all");
        assertThat(visible("models-temperature-slider")).isFalse();
    }

    // IF the loaded figure were not offered, THEN the person would type a window the server may not hold.
    @Test
    void context_nothingStoredLoaded16384_fieldSuggestsItWhileOff() throws TimeoutException {
        capabilities.report("ollama", GEMMA, 131072, 16384);

        openModels();

        assertThat(ThemeTestSupport.onFx(() -> ((ToggleSwitch) required("models-context-on")).isSelected()))
                .isFalse();
        assertThat(modelsField("models-context").getText()).isEqualTo("16384");
        assertThat(modelsField("models-context").isDisabled()).isTrue();
        assertThat(modelForm().dirty().get()).isFalse();
    }

    @Test
    void context_storedForGemma_switchOnAndFieldEditable() throws TimeoutException {
        modelSettings.put(with("ollama", GEMMA, TemperatureChoice.CHOOSE, null, 16384, null));

        openModels();

        assertThat(ThemeTestSupport.onFx(() -> ((ToggleSwitch) required("models-context-on")).isSelected()))
                .isTrue();
        assertThat(modelsField("models-context").getText()).isEqualTo("16384");
        assertThat(modelsField("models-context").isDisabled()).isFalse();
    }

    // IF the Ollama fold offered a field choice, THEN a person would pick a field Ollama never reads.
    @Test
    void advanced_ollamaModel_hasNoFieldChoiceButSaysSo() throws TimeoutException {
        openModels();

        assertThat(visible("models-field-ollama")).isTrue();
        assertThat(shown("models-field-ollama")).isEqualTo("Not used for Ollama, which has its own option.");
        assertThat(visible("models-field-completion")).isFalse();
        assertThat(visible("models-field-tokens")).isFalse();
    }

    @Test
    void advanced_openAiCompatibleModel_offersBothFields() throws TimeoutException {
        openModels();

        chooseModel("lapa");

        assertThat(visible("models-field-ollama")).isFalse();
        assertThat(shown("models-field-completion")).isEqualTo("max_completion_tokens (standard)");
        assertThat(shown("models-field-tokens")).isEqualTo("max_tokens (older servers)");
        assertThat(ThemeTestSupport.onFx(() -> ((ToggleButton) required("models-field-tokens")).isSelected()))
                .isTrue();
    }

    // IF the date or the action were missing, THEN the person could not tell when it happened or how to undo it.
    @Test
    void refused_gptOssTemperature_rowShowsDateWhyAndSendAgain() throws TimeoutException {
        modelSettings.put(refusedTemperature());

        openModels();
        chooseModel(GPT_OSS);

        assertThat(visible("models-refused-row-temperature")).isTrue();
        assertThat(shown("models-refused-why-temperature"))
                .startsWith("Refused on Oct 2.")
                .contains("stopped sending it");
        assertThat(shown("models-refused-send-temperature")).isEqualTo("Try again now");
        assertThat(visible("models-refused-none")).isFalse();
    }

    // IF Try again now waited for Save, THEN the next request would still leave the setting out.
    @Test
    void sendAgain_pressed_rowGoesAndTheStoreForgotIt() throws TimeoutException {
        modelSettings.put(refusedTemperature());
        openModels();
        chooseModel(GPT_OSS);

        press("models-refused-send-temperature");
        awaitFx(() -> !modelForm().sendingAgain().get());

        assertThat(scene.getRoot().lookup("#models-refused-row-temperature")).isNull();
        assertThat(modelSettings.stored("ollama", GPT_OSS).orElseThrow().refused())
                .isEmpty();
        assertThat(visible("models-refused-none")).isTrue();
    }

    // IF the empty list were blank, THEN a person could not tell whether refusals are tracked at all.
    @Test
    void refused_nothingRefused_saysSoAndExplainsWhy() throws TimeoutException {
        openModels();

        assertThat(shown("models-refused-none")).isEqualTo("Nothing refused");
        assertThat(shown("models-refused-none-hint")).startsWith("When a model refuses a setting");
    }

    @Test
    void techDetails_ollamaModel_showsTheNumCtxName() throws TimeoutException {
        openModels();
        assertThat(visible("models-context-tech")).isFalse();

        onFx(() -> injector.getInstance(TechnicalDetails.class).property().set(true));

        assertThat(visible("models-context-tech")).isTrue();
        assertThat(shown("models-context-tech")).isEqualTo("num_ctx (Ollama)");
        assertThat(visible("models-limit-tech")).isTrue();
    }

    @Test
    void context_openAiCompatibleModel_saysNoWindowIsSent() throws TimeoutException {
        openModels();

        chooseModel("lapa");

        assertThat(visible("models-context-openai")).isTrue();
        assertThat(visible("models-context-tech")).isFalse();
    }

    @Test
    void empty_noModelsAnywhere_saysToAddAProvider() throws TimeoutException {
        configs.delete("ollama");
        configs.delete("lmstudio");
        openSettings();
        onFx(() -> viewModel().reloadProviders());
        awaitFx(() -> viewModel().providers().isEmpty());

        onFx(() -> tabs().getSelectionModel().select(modelsTab()));
        awaitFx(() -> !roster().listing().get());

        assertThat(visible("models-empty")).isTrue();
        assertThat(shown("models-empty")).startsWith("No models yet.");
        assertThat(visible("models-scroll")).isFalse();
    }

    // IF the tab kept the list it read on the first visit, THEN a provider deleted meanwhile would still be offered
    // and Save could write a row for it.
    @Test
    void tab_providerDeletedWhileAway_nextVisitOffersOnlyTheRest() throws TimeoutException {
        openModels();
        clickOn("#settings-tab-appearance");
        configs.delete("ollama");
        onFx(() -> viewModel().reloadProviders());
        awaitFx(() -> viewModel().providers().size() == 1);

        onFx(() -> tabs().getSelectionModel().select(modelsTab()));
        awaitFx(() -> !roster().listing().get()
                && "lapa".equals(modelForm().selected().get().modelId()));

        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getItems().size())).isEqualTo(1);
        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getValue().modelId()))
                .isEqualTo("lapa");
    }

    // IF the form waited for the program's answer, THEN a slow server would lock it for seconds.
    @Test
    void tab_programSlowToAnswer_storedSettingsShownAndFormUsable() throws Exception {
        modelSettings.put(with("ollama", GEMMA, TemperatureChoice.CHOOSE, null, 16384, null));
        final CountDownLatch release = new CountDownLatch(1);
        capabilities.holdUntil("ollama", GEMMA, release);
        openSettings();
        onFx(() -> tabs().getSelectionModel().select(modelsTab()));
        awaitFx(() -> !roster().listing().get()
                && modelForm().selected().get() != null
                && !modelForm().reading().get());

        assertThat(modelsField("models-context").getText()).isEqualTo("16384");
        assertThat(ThemeTestSupport.onFx(() ->
                        required("models-body").lookup("#models-temperature").isDisabled()))
                .isFalse();
        assertThat(shown("models-found-text")).startsWith("Asking the program");
        release.countDown();
        awaitFx(() -> !modelForm().facts().get().loading());
    }

    private static ModelSettings refusedTemperature() {
        return new ModelSettings(
                "ollama",
                GPT_OSS,
                TemperatureChoice.OMIT,
                null,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(new RefusedSetting("temperature", LocalDate.of(2026, 10, 2), "omitted")),
                null,
                null);
    }
}
