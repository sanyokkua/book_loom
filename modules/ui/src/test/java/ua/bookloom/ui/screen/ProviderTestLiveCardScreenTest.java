package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.ProbeCall;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ProviderReports;
import ua.bookloom.ui.ScriptedProviderVerifier.Announced;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.MutableClock;

/**
 * The model tests' card: "Test with details" opens it at once on the request as it is sent, it waits with a live time
 * while the model works and fills in when the reply comes, and it stays open until closed; the quiet Test opens
 * nothing, and neither opens the generic progress dialog.
 */
class ProviderTestLiveCardScreenTest extends SettingsScreenTestBase {

    private static final String HEADERS = "Authorization: Bearer ••••\nContent-Type: application/json";
    private static final String WIRE_BODY = "{\"model\":\"gemma3:12b\",\"stream\":true}";
    private static final String SHOWN_BODY = "{\n  \"model\" : \"gemma3:12b\",\n  \"stream\" : true\n}";

    private final List<String> copied = new CopyOnWriteArrayList<>();
    private final MutableClock clock = new MutableClock();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.clipboard(copied::add).clock(clock);
    }

    private static VerificationExchange sentInference() {
        return new VerificationExchange(
                "POST",
                "http://127.0.0.1:11434/api/chat",
                WIRE_BODY,
                "",
                0,
                null,
                null,
                false,
                HEADERS,
                null,
                null,
                SHOWN_BODY,
                "",
                null,
                List.of());
    }

    private void startHeld(final String buttonId) throws Exception {
        verifier.announce(new Announced(VerificationStage.INFERENCE, null, sentInference()));
        verifier.respondWith(ProviderReports.allPassedWithExchanges());
        verifier.hold();
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));
        onFx(() -> button(buttonId).fire());
        verifier.awaitEntered();
    }

    private String area(final String id) {
        return ThemeTestSupport.onFx(((TextArea) required(id))::getText);
    }

    private String shown(final String id) {
        return ThemeTestSupport.onFx(((Labeled) required(id))::getText);
    }

    private boolean cardOpen() {
        return ThemeTestSupport.onFx(() -> optionalNode("test-card") != null);
    }

    // IF the detailed test opened its card only after the run, THEN the person would watch an empty overlay meanwhile.
    @Test
    void testWithDetails_pressed_opensTheCardAtOnceAndKeepsItOpenAfterTheRun() throws Exception {
        startHeld("settings-test-inference-details");

        awaitFx(this::cardOpen);
        verifier.release();
        awaitFx(() -> !viewModel().tests().checking().get());

        assertThat(cardOpen()).isTrue();
        assertThat(area("test-response")).isEqualTo("OK");
    }

    private void startAdvancedHeldInInference() throws Exception {
        verifier.begin(VerificationStage.CONNECTION, VerificationStage.MODELS, VerificationStage.INFERENCE);
        verifier.respondWith(ProviderReports.allPassedWithExchanges());
        verifier.hold();
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));
        onFx(() -> button("settings-test-advanced-details").fire());
        verifier.awaitEntered();
    }

    // IF the Advanced card said only "Testing..." until its first request, THEN a model that loads for a minute would
    // look like a frozen window; it names the step it is in and what it waits for.
    @Test
    void advancedCard_modelStillLoading_namesTheStepAndWhatItWaitsFor() throws Exception {
        startAdvancedHeldInInference();

        awaitFx(() -> optionalNode("test-verdict-text") != null
                && shown("test-verdict-text").startsWith("Step 3 of 4"));
        final List<String> waiting = List.of(shown("test-verdict-text"), area("test-response"));
        verifier.release();
        awaitFx(() -> !viewModel().tests().checking().get());

        assertThat(waiting)
                .containsExactly(
                        "Step 3 of 4 · Inference · waiting for the model to load or answer", "Waiting for the model");
    }

    // IF only the tabs of finished stages opened, THEN the stage that is working could not be looked at.
    @Test
    void advancedCard_inferenceRunning_opensTheTabsOfStagesThatBegunAndNotTheRest() throws Exception {
        startAdvancedHeldInInference();
        awaitFx(() -> optionalNode("test-verdict-text") != null
                && shown("test-verdict-text").startsWith("Step 3 of 4"));

        final List<Boolean> disabled = ThemeTestSupport.onFx(() -> List.of(
                button("test-tab-connection").isDisabled(),
                button("test-tab-models").isDisabled(),
                button("test-tab-inference").isDisabled(),
                button("test-tab-advanced").isDisabled()));
        verifier.release();

        assertThat(disabled).containsExactly(false, false, false, true);
    }

    // IF the quiet test opened a card or the progress overlay, THEN there would be no way to test without one.
    @Test
    void quietTest_pressed_opensNoCardAndNoProgressDialog() throws Exception {
        startHeld("settings-test-inference");
        final ActivityTracker tracker = injector.getInstance(ActivityTracker.class);

        final List<Object> whileRunning = ThemeTestSupport.onFx(() -> List.of(
                tracker.running().stream().anyMatch(a -> a.kind() == ActivityKind.PROVIDER_INFERENCE_TEST),
                tracker.blockingActivity().get() == null,
                optionalNode("test-card") == null));
        verifier.release();
        awaitFx(() -> !viewModel().tests().checking().get());

        assertThat(whileRunning).containsExactly(true, true, true);
        assertThat(cardOpen()).isFalse();
        assertThat(shown("check-result-INFERENCE")).isNotBlank();
    }

    // IF the request appeared only with the reply, THEN the card would say nothing about what the model is answering.
    @Test
    void waitingLine_requestSentReplyPending_showsTheRequestThenTheReplyReplacesIt() throws Exception {
        startHeld("settings-test-inference-details");
        awaitFx(() -> optionalNode("test-request-line") != null
                && !((Labeled) required("test-request-line")).getText().isEmpty());

        final List<String> waiting = List.of(shown("test-request-line"), area("test-body"), area("test-response"));
        verifier.release();
        awaitFx(() -> !viewModel().tests().checking().get());

        assertThat(waiting)
                .containsExactly("POST http://127.0.0.1:11434/api/chat", SHOWN_BODY, "Waiting for the model");
        assertThat(area("test-response")).isEqualTo("OK");
    }

    // IF the time stood still while the request was open, THEN a slow model would look like a frozen window.
    @Test
    void liveTimer_requestOpen_ticksWithTheClock() throws Exception {
        startHeld("settings-test-inference-details");
        awaitFx(() -> optionalNode("test-metric-time") != null);

        clock.advance(Duration.ofMillis(3400));

        awaitFx(() -> "3.4 s".equals(((Labeled) required("test-metric-time")).getText()));
        verifier.release();
        awaitFx(() -> !viewModel().tests().checking().get());
        assertThat(shown("test-metric-time")).isEqualTo("1.2 s");
    }

    // IF Copy as curl took the laid-out body, THEN the command would carry line breaks the server never saw.
    @Test
    void copy_prettyBody_bodyCopyIsLaidOutWhileCurlKeepsTheWireBody() throws Exception {
        startHeld("settings-test-inference-details");
        awaitFx(() -> SHOWN_BODY.equals(((TextArea) required("test-body")).getText()));

        onFx(() -> button("test-copy-body").fire());
        onFx(() -> button("test-copy-curl").fire());
        verifier.release();

        assertThat(copied).hasSize(2);
        assertThat(copied.get(0)).isEqualTo(SHOWN_BODY);
        assertThat(copied.get(1)).contains("--data-raw '" + WIRE_BODY + "'").doesNotContain("\n  \"model\"");
    }

    // IF the Advanced card showed only machine verdicts, THEN a person could not tell what was tested or why.
    @Test
    void advancedCard_finished_explainsEachCheckWithItsCallsTimed() throws TimeoutException {
        verifier.respondWith(advancedWithCalls());
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));

        onFx(() -> button("settings-test-advanced-details").fire());
        awaitFx(() -> !viewModel().tests().checking().get());
        final Node checks = required("test-checks");

        assertThat(shown("test-advanced-intro")).startsWith("This test finds out which request settings");
        assertThat(textsUnder(checks))
                .contains(
                        "Structured answers",
                        "Sent a request that asks for a JSON answer in a fixed shape.",
                        "The answer was exactly the fixed shape.",
                        "Call 1: 1.2 s · 34.0 tok/s",
                        "Turning reasoning off",
                        "Call 1: 800 ms · status 400",
                        "Call 2: 950 ms · 20.0 tok/s");
    }

    // IF closing mid-run dropped the test silently, THEN the person could not choose to let it finish.
    @Test
    void close_whileRunning_asksAndCloseAndKeepRunningLetsTheTestFinish() throws Exception {
        startHeld("settings-test-inference-details");
        awaitFx(this::cardOpen);

        onFx(() -> button("test-close").fire());
        awaitFx(() -> optionalNode("test-close-card") != null);
        onFx(() -> button("test-close-keep").fire());

        assertThat(cardOpen()).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> viewModel().tests().checking().get()))
                .isTrue();
        verifier.release();
        awaitFx(() -> !viewModel().tests().checking().get());
        assertThat(viewModel().tests().stages()).hasSize(3);
    }

    // IF the card had no Stop while the model works, THEN a hung request could only be stopped from the title bar.
    @Test
    void stop_whileRunning_stopsTheTestAndTheCardStays() throws Exception {
        startHeld("settings-test-inference-details");
        awaitFx(() -> optionalNode("test-stop") != null && required("test-stop").isVisible());

        onFx(() -> button("test-stop").fire());

        awaitFx(() -> !viewModel().tests().checking().get());
        assertThat(cardOpen()).isTrue();
        verifier.release();
    }

    private Node optionalNode(final String id) {
        return scene.getRoot().lookup("#" + id);
    }

    private static Result<VerificationReport> advancedWithCalls() {
        final VerificationReport base = java.util.Objects.requireNonNull(
                ProviderReports.advancedPassed("schema=honoured; thinking=think")
                        .data(),
                "report");
        final List<StageOutcome> stages = base.stages().subList(0, 3);
        final StageOutcome advanced = new StageOutcome(
                VerificationStage.ADVANCED,
                StageStatus.PASSED,
                null,
                "schema=honoured; thinking=think",
                Duration.ofMillis(3400),
                null,
                advancedExchange());
        return Result.ok(new VerificationReport(List.of(stages.get(0), stages.get(1), stages.get(2), advanced)));
    }

    private static VerificationExchange advancedExchange() {
        return new VerificationExchange(
                "POST",
                "http://127.0.0.1:11434/api/chat",
                "{}",
                "",
                200,
                null,
                null,
                false,
                HEADERS,
                null,
                null,
                "{}",
                "",
                null,
                advancedCalls());
    }

    private static List<ProbeCall> advancedCalls() {
        return List.of(
                new ProbeCall(
                        AdvancedCheck.SCHEMA,
                        "{}",
                        "{}",
                        "{\n  \"k\" : \"ZX-91\"\n}",
                        200,
                        Duration.ofMillis(1200),
                        34.0),
                new ProbeCall(AdvancedCheck.THINKING, "{}", "{}", "", 400, Duration.ofMillis(800), null),
                new ProbeCall(AdvancedCheck.THINKING, "{}", "{}", "41", 200, Duration.ofMillis(950), 20.0));
    }

    private String firstTokenCell(final Result<VerificationReport> report) throws Exception {
        verifier.respondWith(report);
        openSettings();
        onFx(() -> viewModel().draftModel().set(MODEL));
        onFx(() -> button("settings-test-inference-details").fire());
        awaitFx(() -> !viewModel().tests().checking().get() && optionalNode("test-metric-firstToken") != null);
        return shown("test-metric-firstToken");
    }

    // IF a route that reports no first-token time showed 0, THEN Ollama's OpenAI-compatible route would look instant.
    @Test
    void firstTokenCell_ollamaOpenAiRoute_readsDash() throws Exception {
        assertThat(firstTokenCell(ProviderReports.allPassedWithTimings(null, 12, null)))
                .isEqualTo("–");
    }

    @Test
    void firstTokenCell_mlxWithCachedPrompt_saysUncachedWithTheShare() throws Exception {
        assertThat(firstTokenCell(ProviderReports.allPassedWithTimings(1200L, 600, 200)))
                .isEqualTo("1.2 s · uncached · 33 %");
    }
}
