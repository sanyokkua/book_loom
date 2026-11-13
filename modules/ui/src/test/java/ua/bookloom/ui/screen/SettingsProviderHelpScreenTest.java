package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ProviderEngine;
import ua.bookloom.ui.state.ProviderTest;

/**
 * The help the Providers form gives per engine (LM Studio, Unsloth, llama-server), the privacy line with the host,
 * and the neutral cloud label in the model list, read from the real scene.
 */
class SettingsProviderHelpScreenTest extends SettingsScreenTestBase {

    private static final String LLAMA_HINT = "http://127.0.0.1:9931/v1";

    private ProviderEngine engine() {
        return injector.getInstance(ProviderEngine.class);
    }

    private void addProvider(final String id, final String address, final ServerEngine stored) throws TimeoutException {
        configs.save(new ProviderConfig(
                        id,
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(address),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60))
                .withEngine(stored));
        onFx(() -> viewModel().reloadProviders());
        awaitFx(() -> viewModel().configOf(id).isPresent());
    }

    private void open(final String id) {
        onFx(() -> viewModel().editProvider(id));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void treatAsOnNewProvider(final ServerEngine chosen) {
        openSettings();
        onFx(() -> viewModel().startAdding());
        onFx(() -> engine().treatAs(chosen));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String addressHint() {
        return ThemeTestSupport.onFx(() -> field("settings-form-address").getPromptText());
    }

    private void runTest(final ProviderTest test, final StageOutcome... stages) throws TimeoutException {
        verifier.respondWith(Result.ok(new VerificationReport(List.of(stages))));
        onFx(() -> viewModel().tests().run(test));
        awaitFx(() -> !viewModel().tests().checking().get());
    }

    private static StageOutcome passed(final VerificationStage stage) {
        return new StageOutcome(stage, StageStatus.PASSED, null, null);
    }

    private static StageOutcome failed(final VerificationStage stage, final AppError error) {
        return new StageOutcome(stage, StageStatus.FAILED, error, null);
    }

    // llama-server has no fixed port and its address ends in /v1, so an empty field shows a working shape.
    @Test
    void providerForm_llamaServer_showsAddressHint() {
        treatAsOnNewProvider(ServerEngine.LLAMA_CPP);

        assertThat(addressHint()).isEqualTo(LLAMA_HINT);
    }

    @Test
    void providerForm_lmStudio_keepsItsOwnAddressHint() {
        treatAsOnNewProvider(ServerEngine.LM_STUDIO);

        assertThat(addressHint()).isNotEqualTo(LLAMA_HINT);
    }

    // Unsloth needs a key made in its own settings and has chat tools of its own; the form says both.
    @Test
    void providerForm_unsloth_showsKeyHintAndToolsLine() {
        treatAsOnNewProvider(ServerEngine.UNSLOTH);

        assertThat(isShown("settings-unsloth-help")).isTrue();
        assertThat(text("settings-unsloth-key-hint"))
                .isEqualTo("Create a key in Unsloth › Settings › API and put its environment-variable name here, for"
                        + " example UNSLOTH_API_KEY.");
        assertThat(text("settings-unsloth-tools"))
                .isEqualTo("Unsloth's own chat tools (web search, code) are separate from BookLoom; BookLoom turns"
                        + " them off on every request.");
    }

    @Test
    void providerForm_ollama_showsNoUnslothHelp() {
        openSettings();

        assertThat(isShown("settings-unsloth-help")).isFalse();
    }

    // A refused sign-in on Unsloth says where its key is made, not only that a key is needed.
    @Test
    void testConnection_unslothSignInRefused_namesWhereTheKeyIsMade() throws TimeoutException {
        addProvider("unsloth", "http://127.0.0.1:8888/v1", ServerEngine.UNSLOTH);
        openSettings();
        open("unsloth");
        final AppError refused = AppError.of(
                ErrorCode.auth,
                "Unsloth needs an API key",
                "Unsloth needs an API key.",
                SafeDetails.empty()
                        .withReason(FailureReason.AUTH)
                        .withHttpStatus(401)
                        .withEngine(ServerEngine.UNSLOTH.name())
                        .render(),
                null);

        runTest(ProviderTest.CONNECTION, failed(VerificationStage.CONNECTION, refused));

        assertThat(text("chip-detail-CONNECTION"))
                .isEqualTo("Unsloth needs an API key. Create one in Unsloth › Settings › API and put its"
                        + " environment-variable name here.");
    }

    // LM Studio would answer an unknown id with the loaded model; the stop says why and what to do.
    @Test
    void testModels_lmStudioIdNotListed_saysLmStudioHasNoModel() throws TimeoutException {
        openSettings();
        open("lmstudio");
        final AppError notListed = AppError.of(
                ErrorCode.modelNotFound,
                "Model not found",
                "LM Studio has no model.",
                SafeDetails.empty()
                        .withReason(FailureReason.MODEL_NOT_LISTED)
                        .withModelName("gemma-4-e4b-typo")
                        .withEngine(ServerEngine.LM_STUDIO.name())
                        .render(),
                null);

        runTest(ProviderTest.MODELS, passed(VerificationStage.CONNECTION), failed(VerificationStage.MODELS, notListed));

        assertThat(text("chip-detail-MODELS"))
                .isEqualTo("LM Studio has no model \"gemma-4-e4b-typo\". It would answer with the model that is loaded."
                        + " Pick a model from the list.");
    }

    // A listed id passes without the sentence.
    @Test
    void testModels_lmStudioIdListed_showsNoNoModelSentence() throws TimeoutException {
        openSettings();
        open("lmstudio");

        runTest(ProviderTest.MODELS, passed(VerificationStage.CONNECTION), passed(VerificationStage.MODELS));

        assertThat(isShown("chip-detail-MODELS")).isFalse();
    }

    // The person typed a variant and BookLoom changed it, so the screen says what changed and why, once.
    @Test
    void testModels_lmStudioVariantTyped_removesItAndSaysSoOnce() throws TimeoutException {
        openSettings();
        open("lmstudio");
        onFx(() -> viewModel().setModelText("google/gemma-4-e4b@q4_k_m"));

        runTest(ProviderTest.MODELS, passed(VerificationStage.CONNECTION), passed(VerificationStage.MODELS));

        assertThat(ThemeTestSupport.onFx(() -> viewModel().draftModel().get())).isEqualTo("google/gemma-4-e4b");
        assertThat(text("settings-model-variant-removed"))
                .isEqualTo("\"@q4_k_m\" was removed from the model id: it would load a second copy with default"
                        + " settings.");
        onFx(() -> viewModel().setModelText("google/gemma-4-26b"));
        assertThat(isShown("settings-model-variant-removed")).isFalse();
    }

    // IF Ollama's ids lost what follows an @, THEN a valid Ollama id would be changed by a rule that is LM Studio's.
    @Test
    void testModels_ollamaIdWithAt_isKept() throws TimeoutException {
        openSettings();
        onFx(() -> viewModel().setModelText("team@model"));

        runTest(ProviderTest.MODELS, passed(VerificationStage.CONNECTION), passed(VerificationStage.MODELS));

        assertThat(ThemeTestSupport.onFx(() -> viewModel().draftModel().get())).isEqualTo("team@model");
        assertThat(isShown("settings-model-variant-removed")).isFalse();
    }

    // A provider outside this computer is named by its host; nothing is asked.
    @Test
    void providerArea_remoteProvider_namesTheHostInThePrivacyLine() throws TimeoutException {
        addProvider("openrouter", "https://openrouter.ai/api/v1", ServerEngine.OPENROUTER);
        openSettings();
        open("openrouter");

        assertThat(text("settings-privacy-line"))
                .isEqualTo("Your book stays private when you use a local model. With a provider outside this computer,"
                        + " the text you translate is sent to openrouter.ai.");
        assertThat(scene.getRoot().lookupAll(".check-box")).isEmpty();
    }

    @Test
    void providerArea_localProvider_namesNoHost() {
        openSettings();

        assertThat(text("settings-privacy-line")).isEqualTo("Your book stays private when you use a local model.");
    }

    // LM Studio may write the text to its own log on this computer; the person is told, under the privacy line.
    @ParameterizedTest
    @CsvSource({"lmstudio, true", "ollama, false"})
    void providerArea_selectedProvider_showsLmStudioLogNoteOnlyForLmStudio(final String id, final boolean shown) {
        openSettings();
        open(id);

        assertThat(isShown("settings-privacy-lmstudio-log")).isEqualTo(shown);
    }

    // IF the subtitle promised the text always stays on this computer, THEN it would be false for a remote provider.
    @ParameterizedTest
    @CsvSource({"en, this computer", "uk, на цьому компʼютері"})
    void settingsSubtitle_neverSaysAlwaysLocal(final String language, final String alwaysLocal) {
        useLocale(Locale.forLanguageTag(language));
        openSettings();

        assertThat(text("settings-subtitle")).doesNotContain(alwaysLocal);
    }

    // A person sees which models run on someone else's computer; every model stays listed.
    @Test
    void modelPicker_cloudModel_showsNeutralLabel() throws TimeoutException {
        catalog.respondWith(new ModelInfo("gpt-oss:120b-cloud", true), new ModelInfo("gemma4:e4b", false));
        openSettings();
        awaitFx(() -> modelCombo().getItems().size() == 2);

        assertThat(cellLabel("gpt-oss:120b-cloud")).isEqualTo("cloud");
        assertThat(cellLabel("gemma4:e4b")).isEmpty();
    }

    // The label of the list cell the combo's own factory makes for an id; empty when it carries none.
    private String cellLabel(final String id) {
        return ThemeTestSupport.onFx(() -> {
            final ListCell<String> cell = modelCombo().getCellFactory().call(null);
            ((ModelChoiceCell) cell).updateItem(id, false);
            return cell.getGraphic() != null && cell.getGraphic().lookup(".model-cloud") instanceof Label label
                    ? label.getText()
                    : "";
        });
    }
}
