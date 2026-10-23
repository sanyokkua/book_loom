package ua.bookloom.ui.state;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * Runs the three provider tests against what the Providers form holds — the description as typed, saved or not — and
 * holds the findings of the last one. Nothing is registered or stored for a test, except the seconds per segment a
 * passing inference test of a saved provider measured.
 *
 * <p>A test is a plain submission to the background executor, not a JavaFX {@code Task}: the verifier reports failure
 * by <em>returning</em> it, so {@code Task.setOnFailed} would never fire (D12 of the workspace design). Everything that
 * touches a property runs on the FX Application Thread; the verifier alone runs elsewhere.
 *
 * <p>A test is registered with the {@link ActivityTracker} while it runs — the inference test as model work that no
 * other model work may overlap, the two light tests as provider checks that may run during a run — and is cancelled,
 * its request interrupted, when anything the server would see differently changes (a name does not) or the title bar's
 * Stop is pressed.
 */
@Slf4j
public final class ProviderTestRunner {

    private final ProviderVerifier verifier;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ReadOnlyObjectProperty<Optional<TestSubject>> subject;
    private final ModelSpeedRecorder speeds;
    private final StructuredAnswers structured;
    private final ReadOnlyBooleanWrapper checking = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper checkRefusal = new ReadOnlyStringWrapper("");
    private final ReadOnlyObjectWrapper<@Nullable ProviderTest> running = new ReadOnlyObjectWrapper<>();
    private final Map<ProviderTest, BooleanBinding> availability = new EnumMap<>(ProviderTest.class);
    private final ObservableList<StageChip> stages = FXCollections.observableArrayList();
    private final ObservableList<StageChip> stagesView = FXCollections.unmodifiableObservableList(stages);
    private final ActivityTracker activities;
    private final ObjectBinding<@Nullable ActivityKind> inferenceBlocker;
    private final ReadOnlyObjectWrapper<@Nullable ActivityKind> blockedBy = new ReadOnlyObjectWrapper<>();
    private @Nullable Future<?> inFlight;
    private ActivityTracker.@Nullable Handle handle;

    /**
     * Counts every event that makes a report in flight meaningless: a test starting, another provider, another model.
     * A report is shown only if the count is what it was when its test began; FX thread only.
     */
    private long checkEpoch;

    /**
     * Starts idle, and drops whatever is in flight or on show when the provider or the model changes.
     *
     * @param verifier the port a test is run through
     * @param errors where a refusal of the whole test is shown
     * @param executor the daemon executor the verifier runs on, never the FX thread
     * @param subject what a test is run against: the description in the form, saved or not, and the model typed
     *     for it; empty while the form holds something the rules refuse. Observed for changes
     * @param activities the work under way, which decides whether the inference test may start
     * @param speeds where a speed measured by a passing inference test of a saved provider is kept
     * @param structured where whether the model answered in the structured form is kept for the session
     */
    public ProviderTestRunner(
            final ProviderVerifier verifier,
            final ErrorPresenter errors,
            final ExecutorService executor,
            final ReadOnlyObjectProperty<Optional<TestSubject>> subject,
            final ActivityTracker activities,
            final ModelSpeedRecorder speeds,
            final StructuredAnswers structured) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.activities = Objects.requireNonNull(activities, "activities");
        this.speeds = Objects.requireNonNull(speeds, "speeds");
        this.structured = Objects.requireNonNull(structured, "structured");
        this.inferenceBlocker = activities.blocker(ActivityKind.PROVIDER_INFERENCE_TEST);
        for (final ProviderTest test : ProviderTest.values()) {
            availability.put(
                    test,
                    Bindings.createBooleanBinding(() -> canStart(test), this.subject, checking, inferenceBlocker));
        }
        inferenceBlocker.addListener(observed -> refreshBlocked());
        checking.addListener(observed -> refreshBlocked());
        checking.addListener((observed, was, now) -> {
            if (!now) {
                running.set(null);
            }
        });
        this.subject.addListener((observed, was, now) -> onSubjectChanged(was, now));
    }

    /**
     * Whether a test is running and its report has not arrived.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty checking() {
        return checking.getReadOnlyProperty();
    }

    /**
     * The test whose report has not arrived yet.
     *
     * @return a read-only property holding {@code null} while no test is running
     */
    public ReadOnlyObjectProperty<@Nullable ProviderTest> running() {
        return running.getReadOnlyProperty();
    }

    /**
     * Whether a test may be started now: none is running, a provider is selected, and a model is chosen when the test
     * asks about one.
     *
     * @param test the test to ask about
     * @return a value that follows the model, the provider and the in-progress mark
     */
    public BooleanBinding available(final ProviderTest test) {
        Objects.requireNonNull(test, "test");
        return Objects.requireNonNull(availability.get(test), "every test has an availability");
    }

    /**
     * Why the last test was refused as a whole for a reason the person can fix, e.g. an unknown provider.
     *
     * @return a read-only property holding the error's own message, empty when there is none; it is cleared when a
     *     test starts and when the provider or the model changes
     */
    public ReadOnlyStringProperty checkRefusal() {
        return checkRefusal.getReadOnlyProperty();
    }

    /**
     * The other model work that keeps the inference test from being offered.
     *
     * @return a read-only property holding {@code null} while it is offered or a test of this runner is itself running
     */
    public ReadOnlyObjectProperty<@Nullable ActivityKind> blockedBy() {
        return blockedBy.getReadOnlyProperty();
    }

    /**
     * The findings of the last test: the stages it covers, in stage order, once there is a report.
     *
     * @return a read-only list, empty before any report, after a provider or model change and while a test runs
     */
    public ObservableList<StageChip> stages() {
        return stagesView;
    }

    /**
     * Starts a test against the form's description (and its model, for a test that asks about one). Does nothing
     * while a test is running, the form holds something the rules refuse, or a needed choice is missing. Returns at once; the report arrives on the FX
     * thread. FX thread only.
     *
     * @param test which test to run
     * @return {@code true} if the test started, {@code false} if it was refused
     */
    public boolean run(final ProviderTest test) {
        Objects.requireNonNull(test, "test");
        log.debug(
                "provider test {} pressed: subject present {}",
                test,
                subject.get().isPresent());
        if (!canStart(test)) {
            log.debug(
                    "provider test {} refused: running {}, a model is needed {}",
                    test,
                    checking.get(),
                    test.needsModel());
            return false;
        }
        final TestSubject target = subject.get().orElseThrow();
        log.info(
                "provider test starting: {} on provider {}",
                test,
                target.config().id());
        stages.clear();
        checkRefusal.set("");
        checkEpoch++;
        running.set(test);
        checking.set(true);
        handle = activities.begin(kindOf(test), () -> invalidate("stopped by the person"));
        submit(test, target, checkEpoch);
        return true;
    }

    // A name is only a label: results stay for it. Anything the server would see differently makes them stale.
    private void onSubjectChanged(final Optional<TestSubject> was, final Optional<TestSubject> now) {
        final Optional<TestSubject.Key> before = was.map(TestSubject::key);
        if (before.equals(now.map(TestSubject::key))) {
            log.trace("the test subject changed in its name only: results stay");
        } else {
            invalidate("the provider or the model changed");
        }
    }

    private boolean canStart(final ProviderTest test) {
        final Optional<TestSubject> target = subject.get();
        return !checking.get()
                && target.isPresent()
                && (!test.needsModel() || !target.get().model().isBlank())
                && activities.conflictFor(kindOf(test)).isEmpty();
    }

    private static ActivityKind kindOf(final ProviderTest test) {
        return test == ProviderTest.INFERENCE ? ActivityKind.PROVIDER_INFERENCE_TEST : ActivityKind.PROVIDER_CHECK;
    }

    private void refreshBlocked() {
        final ActivityKind kind = inferenceBlocker.get();
        blockedBy.set(checking.get() ? null : kind);
    }

    /**
     * Makes whatever is in flight or on screen stale. Clearing {@code checking} here, not when the stale report
     * arrives, is what keeps the new selection from looking busy and lets the arrival be ignored outright.
     */
    private void invalidate(final String reason) {
        final boolean somethingShown = checking.get() || !checkRefusal.get().isEmpty() || !stages.isEmpty();
        checkEpoch++;
        final Future<?> running = inFlight;
        if (running != null) {
            log.debug("cancelling the provider test in flight: {}", reason);
            running.cancel(true);
        }
        settle();
        checking.set(false);
        checkRefusal.set("");
        stages.clear();
        if (somethingShown) {
            log.debug("provider test state invalidated, now epoch {}: {}", checkEpoch, reason);
        } else {
            log.trace("nothing to invalidate, now epoch {}: {}", checkEpoch, reason);
        }
    }

    private void submit(final ProviderTest test, final TestSubject target, final long epoch) {
        try {
            inFlight = executor.submit(() -> verifyOffThread(test, target, epoch));
        } catch (RuntimeException rejected) {
            log.error("the provider test could not be submitted", rejected);
            publish(test, target, epoch, Result.err(internalError(rejected)));
        }
    }

    private void verifyOffThread(final ProviderTest test, final TestSubject target, final long epoch) {
        log.debug(
                "running provider test {} for {} on {}",
                test,
                target.config().id(),
                Thread.currentThread().getName());
        final Result<VerificationReport> result = verifyGuarded(test, target);
        Platform.runLater(() -> publish(test, target, epoch, result));
    }

    private Result<VerificationReport> verifyGuarded(final ProviderTest test, final TestSubject target) {
        try {
            final String chosen = test.needsModel() ? target.model().strip() : "";
            return verifier.verify(target.config(), chosen, test.policy());
        } catch (Throwable thrown) {
            log.error("the verifier threw instead of returning a result", thrown);
            return Result.err(internalError(thrown));
        }
    }

    private static AppError internalError(final Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Unexpected error",
                "The provider test stopped because of an unexpected error.",
                null,
                cause);
    }

    private void publish(
            final ProviderTest test,
            final TestSubject target,
            final long epoch,
            final Result<VerificationReport> result) {
        if (epoch != checkEpoch) {
            log.debug("report of test {} discarded: the current one is {}", epoch, checkEpoch);
            return;
        }
        try {
            final AppError refusal = result.error();
            if (refusal != null) {
                refuse(refusal);
            } else {
                show(test, target, Objects.requireNonNull(result.data()));
            }
        } finally {
            settle();
            checking.set(false);
        }
    }

    // The test in flight is over, answered or dropped: its future and its registration go.
    private void settle() {
        inFlight = null;
        final ActivityTracker.Handle registered = handle;
        handle = null;
        if (registered != null) {
            registered.end();
        }
    }

    private void refuse(final AppError refusal) {
        log.debug("the test was refused as a whole with {}", refusal.code());
        if (refusal.code() == ErrorCode.validation) {
            checkRefusal.set(refusal.message());
        } else {
            errors.present(refusal);
        }
    }

    private void show(final ProviderTest test, final TestSubject target, final VerificationReport report) {
        log.debug("showing the report of test {} on {}", test, target.config().id());
        final Map<VerificationStage, StageOutcome> reported = new EnumMap<>(VerificationStage.class);
        report.stages().forEach(outcome -> reported.putIfAbsent(outcome.stage(), outcome));
        final List<StageChip> chips = Arrays.stream(VerificationStage.values())
                .filter(stage -> stage.compareTo(test.lastStage()) <= 0)
                .map(stage -> TestChips.of(stage, reported.get(stage)))
                .toList();
        chips.forEach(TestChips::log);
        stages.setAll(chips);
        keepSpeed(test, target, chips);
        if (chips.stream().allMatch(TestChips::isPass)) {
            log.info("provider test {} passed", test);
        } else {
            log.info("provider test {} did not pass", test);
        }
    }

    // Only a saved provider has a row to keep a speed on, and only the inference test measures one.
    private void keepSpeed(final ProviderTest test, final TestSubject target, final List<StageChip> chips) {
        final String model = target.model().strip();
        final Optional<StageChip> inference = chips.stream()
                .filter(chip -> chip.stage() == VerificationStage.INFERENCE && chip.status() == StageStatus.PASSED)
                .findFirst();
        final boolean keep = test == ProviderTest.INFERENCE && target.stored() && !model.isEmpty();
        log.debug(
                "speed of {} kept {}: needs the inference test {}, a saved provider as saved {}, a model {}, a pass {}",
                target.config().id(),
                keep && inference.isPresent(),
                test == ProviderTest.INFERENCE,
                target.stored(),
                !model.isEmpty(),
                inference.isPresent());
        if (keep && inference.isPresent() && inference.get().elapsed() != null) {
            speeds.record(target.config().id(), model, inference.get().elapsed(), MeasuredFrom.TEST);
        }
        final VerificationExchange exchange = inference.map(StageChip::exchange).orElse(null);
        if (keep && exchange != null) {
            structured.record(target.config().id(), model, exchange.structured());
        }
    }
}
