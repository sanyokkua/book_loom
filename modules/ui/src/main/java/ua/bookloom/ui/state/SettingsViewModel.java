package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * What the settings screen shows and does about providers: which one is selected, which model is chosen for it, and
 * the outcome of checking the pair.
 *
 * <p>A singleton because the screen's controller is rebuilt on every visit while the choice must survive it. The
 * check is a plain submission to the background executor, not a JavaFX {@code Task}: the verifier reports failure by
 * <em>returning</em> it, so {@code Task.setOnFailed} would never fire (D12 of the workspace design). Everything that
 * touches a property runs on the FX Application Thread, which the methods below state; the verifier alone runs
 * elsewhere.
 */
@Slf4j
@Singleton
public final class SettingsViewModel {

    private final ProviderVerifier verifier;
    private final ModelListing modelListing;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ObservableList<ProviderRow> providers;
    private final ReadOnlyStringWrapper selectedProviderId = new ReadOnlyStringWrapper("");
    private final StringProperty model = new SimpleStringProperty("");
    private final ReadOnlyBooleanWrapper checking = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper checkRefusal = new ReadOnlyStringWrapper("");
    private final BooleanBinding checkAvailable;
    private final ObservableList<StageChip> stages = FXCollections.observableArrayList();
    private final ObservableList<StageChip> stagesView = FXCollections.unmodifiableObservableList(stages);

    /**
     * Counts every event that makes a report in flight meaningless: a check starting, another provider, another model.
     * A report is shown only if the count is what it was when its check began; FX thread only.
     */
    private long checkEpoch;

    /**
     * Reads the registry once and selects its first provider in {@link ua.bookloom.api.llm.ProviderKind} order.
     *
     * @param configs the registry the rows are read from; the order it lists in is not relied on
     * @param verifier the port a check is run through
     * @param modelListing the models the selected provider offers; this view model discards and re-asks it when the
     *     provider changes
     * @param toasts where a passed check is announced
     * @param errors where a refusal of the whole check is shown
     * @param executor the daemon executor the verifier runs on, never the FX thread
     */
    @Inject
    public SettingsViewModel(
            final ProviderConfigs configs,
            final ProviderVerifier verifier,
            final ModelListing modelListing,
            final Toasts toasts,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        Objects.requireNonNull(configs, "configs");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.modelListing = Objects.requireNonNull(modelListing, "modelListing");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.providers = FXCollections.unmodifiableObservableList(FXCollections.observableArrayList(rowsOf(configs)));
        this.checkAvailable =
                Bindings.createBooleanBinding(() -> !model.get().isBlank() && !checking.get(), model, checking);
        providers.stream().findFirst().ifPresent(first -> selectedProviderId.set(first.id()));
        model.addListener((observed, was, now) -> onModelChanged());
        log.info("settings ready: {} provider(s), selected '{}'", providers.size(), selectedProviderId.get());
    }

    private static List<ProviderRow> rowsOf(final ProviderConfigs configs) {
        return configs.all().stream()
                .sorted(Comparator.comparing(ProviderConfig::kind).thenComparing(ProviderConfig::id))
                .map(config -> new ProviderRow(config.id(), config.baseUrl().toString()))
                .toList();
    }

    /**
     * The providers on offer, in the order they are listed.
     *
     * @return a read-only list that never changes after construction
     */
    public ObservableList<ProviderRow> providers() {
        return providers;
    }

    /**
     * The id of the one selected provider.
     *
     * @return a read-only property; empty only if the registry held no provider
     */
    public ReadOnlyStringProperty selectedProviderId() {
        return selectedProviderId.getReadOnlyProperty();
    }

    /**
     * The model text for the selected provider, exactly as it was typed or picked; a new selection resets it. A check
     * asks about the text stripped of surrounding whitespace.
     *
     * @return a writable property, empty while none is chosen; FX thread only
     */
    public StringProperty model() {
        return model;
    }

    /**
     * The models the selected provider offers. Typing a name never depends on it.
     *
     * @return the listing this view model discards and re-asks whenever another provider is selected
     */
    public ModelListing modelListing() {
        return modelListing;
    }

    /**
     * Asks the selected provider for its models again. FX thread only.
     */
    public void refreshModels() {
        log.debug("model list refresh requested for '{}'", selectedProviderId.get());
        modelListing.refresh(selectedProviderId.get());
    }

    /**
     * Takes the model entry's text as it stands, unstripped and unchecked, so the check becomes available as soon as
     * something is typed: a disabled button never takes the click that would otherwise commit the entry. FX thread
     * only.
     *
     * @param text what the entry holds now; blank means no model is chosen
     */
    public void setModelText(final String text) {
        Objects.requireNonNull(text, "text");
        model.set(text);
    }

    /** Notes that the person settled on the entry (Enter, or a pick from the list); the text itself already counts. */
    public void commitModel() {
        final String chosen = model.get().strip();
        if (chosen.isEmpty()) {
            log.debug("model entry committed while blank");
        } else {
            log.info("model chosen: {}", chosen);
        }
    }

    /**
     * Whether a check is running and its report has not arrived.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty checking() {
        return checking.getReadOnlyProperty();
    }

    /**
     * Whether a check may be started now: a model is chosen and none is already running.
     *
     * @return a value that follows the model and the in-progress mark
     */
    public ObservableBooleanValue checkAvailable() {
        return checkAvailable;
    }

    /**
     * Why the last check was refused as a whole for a reason the person can fix, e.g. an unknown provider.
     *
     * @return a read-only property holding the error's own message, empty when there is none; it is cleared when a
     *     check starts and when the provider or the model changes
     */
    public ReadOnlyStringProperty checkRefusal() {
        return checkRefusal.getReadOnlyProperty();
    }

    /**
     * The findings of the last check, always all three in stage order once there is a report.
     *
     * @return a read-only list, empty before any report, after a provider or model change and while a check runs
     */
    public ObservableList<StageChip> stages() {
        return stagesView;
    }

    /**
     * Selects a provider; a different one also discards the model, the offered models and the last findings, which
     * meant something only for the server they came from, then asks the new provider for its models. FX thread only.
     *
     * @param id a provider id; an id no row carries is ignored
     */
    public void selectProvider(final String id) {
        Objects.requireNonNull(id, "id");
        log.debug("select provider '{}' requested", id);
        if (providers.stream().noneMatch(row -> row.id().equals(id))) {
            log.debug("selection ignored: no provider '{}' is listed", id);
        } else if (id.equals(selectedProviderId.get())) {
            log.debug("selection ignored: '{}' is already selected", id);
        } else {
            selectedProviderId.set(id);
            invalidateCheck("the provider changed");
            model.set("");
            modelListing.discard();
            modelListing.refresh(id);
            log.info("provider selected: {}", id);
        }
    }

    /**
     * The provider and model a run or a check is made with.
     *
     * @return the selected provider with the chosen model stripped of surrounding whitespace, or empty while no model
     *     is chosen or the registry held no provider; FX thread only
     */
    public Optional<ModelSelection> selection() {
        final String provider = selectedProviderId.get();
        final String chosen = model.get().strip();
        final boolean complete = !provider.isEmpty() && !chosen.isEmpty();
        log.debug("selection complete {}: provider '{}', model chosen {}", complete, provider, !chosen.isEmpty());
        return complete ? Optional.of(new ModelSelection(provider, chosen)) : Optional.empty();
    }

    /**
     * Starts checking the selected provider against the chosen model, in all three stages. Does nothing while no
     * model is chosen or a check is already running. Returns at once; the report arrives on the FX thread. FX thread
     * only.
     */
    public void check() {
        if (checking.get()) {
            log.debug("check refused: one is already running");
            return;
        }
        final Optional<ModelSelection> selection = selection();
        if (selection.isEmpty()) {
            log.debug("check refused: no model is chosen");
            return;
        }
        final ModelSelection chosen = selection.get();
        log.info("provider check starting: provider {}, model {}", chosen.providerId(), chosen.modelId());
        stages.clear();
        checkRefusal.set("");
        checkEpoch++;
        checking.set(true);
        submit(chosen, checkEpoch);
    }

    private void onModelChanged() {
        invalidateCheck("the model changed");
    }

    /**
     * Makes whatever is in flight or on screen stale. Clearing {@code checking} here, not when the stale report
     * arrives, is what keeps the new selection from looking busy and lets the arrival be ignored outright.
     */
    private void invalidateCheck(final String reason) {
        final boolean somethingShown = checking.get() || !checkRefusal.get().isEmpty() || !stages.isEmpty();
        checkEpoch++;
        checking.set(false);
        checkRefusal.set("");
        stages.clear();
        if (somethingShown) {
            log.debug("check state invalidated, now epoch {}: {}", checkEpoch, reason);
        } else {
            log.trace("nothing to invalidate, now epoch {}: {}", checkEpoch, reason);
        }
    }

    private void submit(final ModelSelection selection, final long epoch) {
        try {
            executor.execute(() -> verifyOffThread(selection, epoch));
        } catch (RuntimeException rejected) {
            log.error("the provider check could not be submitted", rejected);
            publish(epoch, Result.err(internalError(rejected)));
        }
    }

    private void verifyOffThread(final ModelSelection selection, final long epoch) {
        log.debug(
                "verifying provider {} on {}",
                selection.providerId(),
                Thread.currentThread().getName());
        final Result<VerificationReport> result = verifyGuarded(selection);
        Platform.runLater(() -> publish(epoch, result));
    }

    private Result<VerificationReport> verifyGuarded(final ModelSelection selection) {
        try {
            return verifier.verify(selection, VerificationPolicy.FULL);
        } catch (Throwable thrown) {
            log.error("the verifier threw instead of returning a result", thrown);
            return Result.err(internalError(thrown));
        }
    }

    private static AppError internalError(final Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Unexpected error",
                "The provider check stopped because of an unexpected error.",
                null,
                cause);
    }

    private void publish(final long epoch, final Result<VerificationReport> result) {
        if (epoch != checkEpoch) {
            log.debug("report of check {} discarded: the current one is {}", epoch, checkEpoch);
            return;
        }
        try {
            final AppError refusal = result.error();
            if (refusal != null) {
                refuse(refusal);
            } else {
                show(Objects.requireNonNull(result.data()));
            }
        } finally {
            checking.set(false);
        }
    }

    private void refuse(final AppError refusal) {
        log.debug("the check was refused as a whole with {}", refusal.code());
        if (refusal.code() == ErrorCode.validation) {
            checkRefusal.set(refusal.message());
        } else {
            errors.present(refusal);
        }
    }

    private void show(final VerificationReport report) {
        final Map<VerificationStage, StageOutcome> reported = new EnumMap<>(VerificationStage.class);
        report.stages().forEach(outcome -> reported.putIfAbsent(outcome.stage(), outcome));
        final List<StageChip> chips = Arrays.stream(VerificationStage.values())
                .map(stage -> chipOf(stage, reported.get(stage)))
                .toList();
        chips.forEach(SettingsViewModel::logStage);
        stages.setAll(chips);
        if (chips.stream().allMatch(SettingsViewModel::isPass)) {
            log.info("provider check passed");
            toasts.success(MessageKey.TOAST_PROVIDER_PASSED);
        } else {
            log.info("provider check did not pass");
        }
    }

    private static StageChip chipOf(final VerificationStage stage, final @Nullable StageOutcome outcome) {
        return outcome == null
                ? new StageChip(stage, StageStatus.SKIPPED, null, null)
                : new StageChip(stage, outcome.status(), outcome.error(), outcome.note());
    }

    private static boolean isPass(final StageChip chip) {
        return chip.status() == StageStatus.PASSED || chip.status() == StageStatus.SOFT_PASS;
    }

    private static void logStage(final StageChip chip) {
        log.info("stage {}: {}", chip.stage(), chip.status());
        final AppError error = chip.error();
        if (chip.status() == StageStatus.FAILED) {
            log.warn("stage {} failed with {}", chip.stage(), error == null ? "no error code" : error.code());
        }
    }
}
