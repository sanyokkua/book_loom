package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.ProviderEngine;
import ua.bookloom.ui.state.ProviderTest;

/** The engine found at a provider's address on the Providers form: the line, Detect again, the choice and the offer. */
class SettingsProviderEngineScreenTest extends SettingsScreenTestBase {

    private static final String OLLAMA_V1 = "http://127.0.0.1:11434/v1";

    private static EngineDetection detected(final ServerEngine engine, final String version, final boolean offer) {
        return new EngineDetection(engine, version, false, 3, Source.DETECTED, Suitability.SUITABLE, offer);
    }

    private static Result<VerificationReport> connectedWith(final EngineDetection engine) {
        final StageOutcome passed = new StageOutcome(VerificationStage.CONNECTION, StageStatus.PASSED, null, null);
        return Result.ok(new VerificationReport(List.of(passed), engine));
    }

    @SuppressWarnings("unchecked")
    private ComboBox<ProviderTreatAsField.Option> treatAs() {
        return (ComboBox<ProviderTreatAsField.Option>) required("settings-form-treat-as");
    }

    private ProviderEngine engine() {
        return injector.getInstance(ProviderEngine.class);
    }

    /** An OpenAI-compatible provider saved at Ollama's own port, opened in the form. */
    private void openOllamaBehindOpenAi() throws TimeoutException {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));
        WaitForAsyncUtils.waitForFxEvents();
        setField("settings-form-address", OLLAMA_V1);
        save();
    }

    private void testConnection(final Result<VerificationReport> report) throws TimeoutException {
        verifier.respondWith(report);
        onFx(() -> viewModel().tests().run(ProviderTest.CONNECTION));
        awaitFx(() -> !viewModel().tests().checking().get());
    }

    private void detectAgain(final EngineDetection found) throws TimeoutException {
        verifier.detectWith(Result.ok(found));
        onFx(() -> button("settings-engine-detect").fire());
        awaitFx(() -> !engine().detecting().get());
    }

    // The person sees which program BookLoom thinks answers, so a wrong guess is visible; Save asks the server.
    @Test
    void providerForm_detectedEngine_showsTheDetectedLine() throws TimeoutException {
        verifier.respondWith(connectedWith(detected(ServerEngine.OLLAMA, "0.40.11", false)));
        openSettings();
        setField("settings-form-request-timeout", "240");

        save();
        awaitFx(() -> text("settings-engine-line").contains("0.40.11"));

        assertThat(text("settings-engine-line")).isEqualTo("Detected: Ollama 0.40.11 · native connection");
        assertThat(verifier.connectionCalls()).containsExactly("ollama");
        assertThat(required("settings-form-dirty").isVisible()).isFalse();
    }

    // An unknown server is said to be one, rather than shown as some engine it is not.
    @Test
    void providerForm_genericServer_saysNotRecognised() throws TimeoutException {
        configs.save(new ProviderConfig(
                "office",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:8000/v1"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60)));
        openSettings();
        onFx(() -> viewModel().reloadProviders());
        awaitFx(() -> viewModel().configOf("office").isPresent());
        onFx(() -> viewModel().editProvider("office"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(text("settings-engine-line")).isEqualTo("Not recognised: generic OpenAI-compatible server");
    }

    // IF Detect again asked the model, THEN it would cost model time and queue behind a run.
    @Test
    void providerForm_detectAgain_updatesLineAndSendsNoModelRequest() throws TimeoutException {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));
        WaitForAsyncUtils.waitForFxEvents();

        detectAgain(detected(ServerEngine.LLAMA_CPP, "b11669", false));

        assertThat(text("settings-engine-line")).isEqualTo("Detected: llama-server b11669 · OpenAI-compatible");
        assertThat(verifier.detections()).extracting(ProviderConfig::id).containsExactly("lmstudio");
        assertThat(verifier.callCount()).isZero();
        assertThat(verifier.selections()).isEmpty();
    }

    // A detection is a few GETs, not model work: a running translation does not hold it back.
    @Test
    void providerForm_detectAgain_whileRunTranslates_isAllowed() throws TimeoutException {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.TRANSLATION, () -> {}));
        WaitForAsyncUtils.waitForFxEvents();

        detectAgain(detected(ServerEngine.LM_STUDIO, "", false));

        assertThat(text("settings-engine-line")).isEqualTo("Detected: LM Studio · OpenAI-compatible");
        assertThat(isShown("settings-engine-unanswered")).isFalse();
    }

    // Nothing answering keeps the last line and says why it did not change.
    @Test
    void providerForm_detectAgainUnanswered_keepsTheLineAndSaysSo() throws TimeoutException {
        openSettings();

        verifier.detectWith(Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "No answer.")));
        onFx(() -> button("settings-engine-detect").fire());
        awaitFx(() -> !engine().detecting().get());

        assertThat(text("settings-engine-line")).isEqualTo("Detected: Ollama · native connection");
        assertThat(isShown("settings-engine-unanswered")).isTrue();
        assertThat(text("settings-engine-unanswered"))
                .isEqualTo("The server does not answer, so the engine was not detected again.");
    }

    // The choice lasts: after Save the registry holds it, and the form shows it chosen.
    @Test
    void providerForm_treatAs_storesEngineChoice() throws TimeoutException {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));
        WaitForAsyncUtils.waitForFxEvents();

        onFx(() -> treatAs().setValue(new ProviderTreatAsField.Option(ServerEngine.LLAMA_CPP)));
        save();

        final ProviderConfig stored = configs.find("lmstudio").orElseThrow();
        assertThat(stored.engineChoice()).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(stored.effectiveEngine()).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(ThemeTestSupport.onFx(() -> treatAs().getValue().engine())).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(ThemeTestSupport.onFx(() -> treatAs().getItems().stream()
                        .map(ProviderTreatAsField.Option::engine)
                        .toList()))
                .containsExactly(
                        null,
                        ServerEngine.OLLAMA,
                        ServerEngine.LM_STUDIO,
                        ServerEngine.LLAMA_CPP,
                        ServerEngine.UNSLOTH,
                        ServerEngine.OPENROUTER,
                        ServerEngine.GENERIC);
    }

    // The offer is the person's choice: shown once, nothing switched; Keep as is declines only the offer.
    @Test
    void providerForm_ollamaBehindOpenAi_offersOnceAndKeepAsIsSetsDeclinedNotChoice() throws TimeoutException {
        openOllamaBehindOpenAi();
        testConnection(connectedWith(detected(ServerEngine.OLLAMA, "0.40.11", true)));
        final boolean offered = isShown("settings-engine-offer");
        final String offerText = text("settings-engine-offer-text");

        onFx(() -> button("settings-engine-offer-keep").fire());
        save();
        testConnection(connectedWith(detected(ServerEngine.OLLAMA, "0.40.11", true)));

        assertThat(offered).isTrue();
        assertThat(offerText)
                .isEqualTo("This is Ollama. Use Ollama's own connection (recommended)? It keeps the window, overflow"
                        + " and sampling controls.");
        assertThat(isShown("settings-engine-offer")).isFalse();
        final ProviderConfig stored = configs.find("lmstudio").orElseThrow();
        assertThat(stored.nativeOfferDeclined()).isTrue();
        assertThat(stored.engineChoice()).isNull();
        assertThat(stored.kind()).isEqualTo(ProviderKind.OPENAI_COMPATIBLE);
        assertThat(stored.baseUrl()).isEqualTo(URI.create(OLLAMA_V1));
    }

    // Use it switches the provider to Ollama's own connection at the host root, and the offer is gone.
    @Test
    void providerForm_useIt_rewritesBaseUrlAndKind() throws TimeoutException {
        openOllamaBehindOpenAi();
        testConnection(connectedWith(detected(ServerEngine.OLLAMA, "0.40.11", true)));

        onFx(() -> button("settings-engine-offer-use").fire());
        save();

        final ProviderConfig stored = configs.find("lmstudio").orElseThrow();
        assertThat(stored.kind()).isEqualTo(ProviderKind.OLLAMA);
        assertThat(stored.baseUrl()).isEqualTo(URI.create("http://127.0.0.1:11434"));
        assertThat(isShown("settings-engine-offer")).isFalse();
    }

    // Detect again drops the person's choice but never a declined offer, which never shows as a choice either.
    @Test
    void providerForm_keepAsIsThenDetectAgain_noOfferAndTreatAsEmpty() throws TimeoutException {
        openOllamaBehindOpenAi();
        testConnection(connectedWith(detected(ServerEngine.OLLAMA, "0.40.11", true)));
        onFx(() -> button("settings-engine-offer-keep").fire());
        save();

        detectAgain(detected(ServerEngine.OLLAMA, "0.40.11", true));

        assertThat(isShown("settings-engine-offer")).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> treatAs().getValue().engine())).isNull();
        assertThat(verifier.detections().getLast().nativeOfferDeclined()).isTrue();
        assertThat(verifier.detections().getLast().engineChoice()).isNull();
    }

    // IF an app that cannot translate a book could be named an engine, THEN a person could "fix" what cannot work.
    @Test
    void providerForm_notSuitableServer_offersNoOverride() throws TimeoutException {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));
        WaitForAsyncUtils.waitForFxEvents();
        final boolean offeredBefore = isShown("settings-form-treat-as");
        final AppError documents = AppError.of(
                ErrorCode.validation,
                "This app cannot be used",
                "This address chats with documents, not with a model.",
                SafeDetails.empty()
                        .withReason(FailureReason.NOT_SUITABLE_DOCUMENTS)
                        .render(),
                null);

        testConnection(Result.ok(new VerificationReport(List.of(new StageOutcome(
                VerificationStage.CONNECTION,
                StageStatus.FAILED,
                documents,
                null,
                Duration.ofMillis(5),
                null,
                null)))));

        assertThat(offeredBefore).isTrue();
        assertThat(isShown("settings-form-treat-as")).isFalse();
    }
}
