package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Model work that competes for the model holds what would start more of it: a run's start, a segment retry, the
 * provider's inference test, while the light provider checks stay offered; and a running translation is registered
 * from the run state.
 */
class ActivityGuardsTest extends ReviewViewModelTestBase {

    private ActivityTracker.Handle begin(final ActivityKind kind) {
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        press(() -> handle.set(activities.begin(kind, null)));
        return Objects.requireNonNull(handle.get(), "handle");
    }

    // IF a run could start while the glossary scan asks the model, THEN both would queue in the gate and the run would
    // look stalled; start is held, and offered again once the scan ends.
    @Test
    void start_whileAScanRuns_isHeldWithTheScanNamedAndReleasedAfterIt() {
        buildViewModel();
        final ActivityTracker.Handle scan = begin(ActivityKind.GLOSSARY_SCAN);

        final ControlState held = controls().start();
        final ActivityKind named = onFx(() -> viewModel.otherWork().get());
        press(scan::end);

        assertThat(held).isEqualTo(ControlState.DISABLED);
        assertThat(named).isEqualTo(ActivityKind.GLOSSARY_SCAN);
        assertThat(controls().start()).isEqualTo(ControlState.ENABLED);
    }

    // IF the run's own registration counted as other work, THEN pause and stop would be held during every run.
    @Test
    void controls_whileTheRunTranslates_registerTheRunButHoldNothing() {
        buildViewModel();

        setRunState(RunState.RUNNING);

        assertThat(onFx(() -> activities.running().stream().map(Activity::kind).toList()))
                .containsExactly(ActivityKind.TRANSLATION);
        assertThat(onFx(() -> viewModel.otherWork().get())).isNull();
        assertThat(controls().pause()).isEqualTo(ControlState.ENABLED);
    }

    // IF a paused run still counted as model work, THEN nothing else could be done while it waits.
    @Test
    void run_paused_isNoLongerRegistered() {
        buildViewModel();
        setRunState(RunState.RUNNING);

        setRunState(RunState.PAUSED);

        assertThat(onFx(() -> activities.running().isEmpty())).isTrue();
    }

    // IF a retry ran beside an export's consistency pass, THEN the two would compete for the model; it is refused in
    // place, naming the export, and the desk is never asked.
    @Test
    void retry_whileAnExportRuns_isRefusedInPlaceNamingIt() {
        buildReview();
        chooseModel(MODEL);
        select("ch05.xhtml:11");
        setRunState(RunState.PAUSED);
        begin(ActivityKind.EXPORT);

        press(() -> review.retry(null, false));

        assertThat(onFx(() -> review.problem().get()))
                .isEqualTo("Unavailable while “Export” is running. Wait for it to finish or stop it.");
        assertThat(desk.calls()).noneMatch(call -> call.startsWith("retry("));
    }

    // IF the inference test were offered during a run, THEN it would wait behind the run's call and time out; the
    // connection test asks no model and stays offered.
    @Test
    void providerTests_whileTheRunTranslates_holdInferenceAndKeepConnection() {
        buildViewModel();
        chooseModel(MODEL);
        openForm();

        setRunState(RunState.RUNNING);

        assertThat(onFx(() -> settings.tests().available(ProviderTest.INFERENCE).get()))
                .isFalse();
        assertThat(onFx(() -> settings.tests().blockedBy().get())).isEqualTo(ActivityKind.TRANSLATION);
        assertThat(onFx(() ->
                        settings.tests().available(ProviderTest.CONNECTION).get()))
                .isTrue();
    }

    // IF the Advanced features stage ran during a run, THEN its calls would queue behind the run's in the one gate and
    // time out; it is refused the way the inference test is, before the verifier is asked.
    @Test
    void advancedStage_runTranslating_refusedBeforeAnyCall() {
        buildViewModel();
        chooseModel(MODEL);
        openForm();
        setRunState(RunState.RUNNING);
        final String typedModel = onFx(
                () -> settings.testedSubject().get().map(TestSubject::model).orElse(""));

        final boolean started = onFx(() -> settings.tests().run(ProviderTest.ADVANCED));

        assertThat(typedModel).as("only the run holds the stage back").isEqualTo(MODEL);
        assertThat(started).isFalse();
        assertThat(settingsVerifier.callCount()).isZero();
        assertThat(onFx(() -> settings.tests().blockedBy().get())).isEqualTo(ActivityKind.TRANSLATION);
    }

    // A paused run holds no model call, so the stage runs then, with its own depth, and no second refusal exists.
    @Test
    void advancedStage_runPaused_runs() throws InterruptedException {
        buildViewModel();
        chooseModel(MODEL);
        openForm();
        setRunState(RunState.RUNNING);
        setRunState(RunState.PAUSED);

        final boolean started = onFx(() -> settings.tests().run(ProviderTest.ADVANCED));
        settingsVerifier.awaitEntered();

        assertThat(started).isTrue();
        assertThat(settingsVerifier.policies()).containsExactly(VerificationPolicy.ADVANCED);
        assertThat(settingsVerifier.selections())
                .extracting(ModelSelection::modelId)
                .containsExactly(MODEL);
    }

    // The form publishes what a test is run against, with the model typed in it; without one no model test is offered.
    private void openForm() {
        press(() -> settings.draftModel().set(MODEL));
        onFx(() -> new ProviderForm(
                settings,
                new FakeProviderConfigs(),
                new ScriptedModelSettings(),
                new MemorySettings(),
                name -> false,
                new TechnicalDetails(new MemorySettings(), Runnable::run, Runnable::run),
                new DirectExecutor()));
    }

    // IF the message did not name the work, THEN a person could not tell what to wait for.
    @Test
    void blockedMessage_ukrainian_namesTheWorkInQuotes() {
        final Messages uk = new Messages(() -> Locale.forLanguageTag("uk"));

        final String text = uk.get(MessageKey.ACTIVITY_BLOCKED, uk.get(ActivityKind.GLOSSARY_SCAN.label()));

        assertThat(text).isEqualTo("Недоступно, доки триває «Пошук імен». Дочекайтеся завершення або зупиніть.");
    }
}
