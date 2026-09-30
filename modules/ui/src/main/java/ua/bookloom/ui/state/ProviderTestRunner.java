package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * Runs the three provider tests for whatever provider and model the settings view model has selected, and holds the
 * findings of the last one.
 *
 * <p>A test is a plain submission to the background executor, not a JavaFX {@code Task}: the verifier reports failure
 * by <em>returning</em> it, so {@code Task.setOnFailed} would never fire (D12 of the workspace design). Everything that
 * touches a property runs on the FX Application Thread; the verifier alone runs elsewhere.
 */
@Slf4j
public final class ProviderTestRunner {

    private final ProviderVerifier verifier;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ReadOnlyStringProperty providerId;
    private final ReadOnlyStringProperty model;
    private final ReadOnlyBooleanWrapper checking = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper checkRefusal = new ReadOnlyStringWrapper("");
    private final Map<ProviderTest, BooleanBinding> availability = new EnumMap<>(ProviderTest.class);
    private final ObservableList<StageChip> stages = FXCollections.observableArrayList();
    private final ObservableList<StageChip> stagesView = FXCollections.unmodifiableObservableList(stages);

    /**
     * Counts every event that makes a report in flight meaningless: a test starting, another provider, another model.
     * A report is shown only if the count is what it was when its test began; FX thread only.
     */
    private long checkEpoch;

    /**
     * Starts idle, and drops whatever is in flight or on show when the provider or the model changes.
     *
     * @param verifier the port a test is run through
     * @param toasts where a fully passed test is announced
     * @param errors where a refusal of the whole test is shown
     * @param executor the daemon executor the verifier runs on, never the FX thread
     * @param providerId the selected provider's id; read when a test is asked for, observed for changes
     * @param model the chosen model text, blank while none is chosen; observed for changes
     */
    public ProviderTestRunner(
            final ProviderVerifier verifier,
            final Toasts toasts,
            final ErrorPresenter errors,
            final ExecutorService executor,
            final ReadOnlyStringProperty providerId,
            final ReadOnlyStringProperty model) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.model = Objects.requireNonNull(model, "model");
        for (final ProviderTest test : ProviderTest.values()) {
            availability.put(
                    test, Bindings.createBooleanBinding(() -> canStart(test), this.providerId, this.model, checking));
        }
        this.providerId.addListener((observed, was, now) -> invalidate("the provider changed"));
        this.model.addListener((observed, was, now) -> invalidate("the model changed"));
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
     * The findings of the last test: the stages it covers, in stage order, once there is a report.
     *
     * @return a read-only list, empty before any report, after a provider or model change and while a test runs
     */
    public ObservableList<StageChip> stages() {
        return stagesView;
    }

    /**
     * Starts a test against the selected provider (and the chosen model, for a test that asks about one). Does
     * nothing while a test is running or a needed choice is missing. Returns at once; the report arrives on the FX
     * thread. FX thread only.
     *
     * @param test which test to run
     */
    public void run(final ProviderTest test) {
        Objects.requireNonNull(test, "test");
        final String chosenModel = model.get().strip();
        log.debug("provider test {} pressed: provider '{}', model '{}'", test, providerId.get(), chosenModel);
        if (!canStart(test)) {
            log.debug(
                    "provider test {} refused: running {}, a model is needed {}",
                    test,
                    checking.get(),
                    test.needsModel());
            return;
        }
        log.info("provider test starting: {} on provider {}", test, providerId.get());
        stages.clear();
        checkRefusal.set("");
        checkEpoch++;
        checking.set(true);
        submit(test, providerId.get(), chosenModel, checkEpoch);
    }

    private boolean canStart(final ProviderTest test) {
        return !checking.get()
                && !providerId.get().isEmpty()
                && (!test.needsModel() || !model.get().isBlank());
    }

    /**
     * Makes whatever is in flight or on screen stale. Clearing {@code checking} here, not when the stale report
     * arrives, is what keeps the new selection from looking busy and lets the arrival be ignored outright.
     */
    private void invalidate(final String reason) {
        final boolean somethingShown = checking.get() || !checkRefusal.get().isEmpty() || !stages.isEmpty();
        checkEpoch++;
        checking.set(false);
        checkRefusal.set("");
        stages.clear();
        if (somethingShown) {
            log.debug("provider test state invalidated, now epoch {}: {}", checkEpoch, reason);
        } else {
            log.trace("nothing to invalidate, now epoch {}: {}", checkEpoch, reason);
        }
    }

    private void submit(final ProviderTest test, final String provider, final String chosenModel, final long epoch) {
        try {
            executor.execute(() -> verifyOffThread(test, provider, chosenModel, epoch));
        } catch (RuntimeException rejected) {
            log.error("the provider test could not be submitted", rejected);
            publish(test, epoch, Result.err(internalError(rejected)));
        }
    }

    private void verifyOffThread(
            final ProviderTest test, final String provider, final String chosenModel, final long epoch) {
        log.debug(
                "running provider test {} for {} on {}",
                test,
                provider,
                Thread.currentThread().getName());
        final Result<VerificationReport> result = verifyGuarded(test, provider, chosenModel);
        Platform.runLater(() -> publish(test, epoch, result));
    }

    private Result<VerificationReport> verifyGuarded(
            final ProviderTest test, final String provider, final String chosenModel) {
        try {
            return test == ProviderTest.CONNECTION
                    ? verifier.verifyConnection(provider)
                    : verifier.verify(new ModelSelection(provider, chosenModel), test.policy());
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

    private void publish(final ProviderTest test, final long epoch, final Result<VerificationReport> result) {
        if (epoch != checkEpoch) {
            log.debug("report of test {} discarded: the current one is {}", epoch, checkEpoch);
            return;
        }
        try {
            final AppError refusal = result.error();
            if (refusal != null) {
                refuse(refusal);
            } else {
                show(test, Objects.requireNonNull(result.data()));
            }
        } finally {
            checking.set(false);
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

    private void show(final ProviderTest test, final VerificationReport report) {
        final Map<VerificationStage, StageOutcome> reported = new EnumMap<>(VerificationStage.class);
        report.stages().forEach(outcome -> reported.putIfAbsent(outcome.stage(), outcome));
        final List<StageChip> chips = Arrays.stream(VerificationStage.values())
                .filter(stage -> stage.compareTo(test.lastStage()) <= 0)
                .map(stage -> chipOf(stage, reported.get(stage)))
                .toList();
        chips.forEach(ProviderTestRunner::logStage);
        stages.setAll(chips);
        if (chips.stream().allMatch(ProviderTestRunner::isPass)) {
            log.info("provider test {} passed", test);
            toasts.success(MessageKey.TOAST_PROVIDER_PASSED);
        } else {
            log.info("provider test {} did not pass", test);
        }
    }

    private static StageChip chipOf(final VerificationStage stage, final @Nullable StageOutcome outcome) {
        return outcome == null
                ? new StageChip(stage, StageStatus.SKIPPED, null, null)
                : new StageChip(
                        stage, outcome.status(), outcome.error(), outcome.note(), outcome.elapsed(), outcome.count());
    }

    private static boolean isPass(final StageChip chip) {
        return chip.status() == StageStatus.PASSED || chip.status() == StageStatus.SOFT_PASS;
    }

    private static void logStage(final StageChip chip) {
        final Duration elapsed = chip.elapsed();
        log.info(
                "stage {}: {} in {} ms, {} model(s)",
                chip.stage(),
                chip.status(),
                elapsed == null ? "?" : elapsed.toMillis(),
                chip.count() == null ? "-" : chip.count());
        final AppError error = chip.error();
        if (chip.status() == StageStatus.FAILED) {
            log.warn("stage {} failed with {}", chip.stage(), error == null ? "no error code" : error.code());
        }
    }
}
