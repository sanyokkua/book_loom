package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * What the Providers form shows and does about the program behind the address: the engine a Save, a Test or Detect
 * again found, the person's "Treat this server as…" choice, and the one-time offer of Ollama's own connection.
 *
 * <p>A detection is only a few GETs, never a model request, so Detect again is not registered as model work and runs
 * while a translation is translating. Every property is FX-thread only; the verifier runs on the background executor.
 */
@Slf4j
@Singleton
public final class ProviderEngine {

    private static final Pattern OPENAI_SUFFIX = Pattern.compile("/+(v1/*)?$");

    private final SettingsViewModel viewModel;
    private final ProviderForm form;
    private final ProviderVerifier verifier;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<@Nullable EngineDetection> detection = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper detecting = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper unanswered = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper unsuitable = new ReadOnlyBooleanWrapper(false);
    private final BooleanBinding offered;
    private final BooleanBinding lineShown;
    private final VariantRemoval variants;

    // Counts the events that make a detection in flight meaningless (another provider, a newer detection); FX only.
    private long epoch;

    /**
     * Follows the open provider, the reports of its tests and its saves.
     *
     * @param viewModel which provider is open and what a test would be run against
     * @param form the draft the engine facts are kept in, and its saves
     * @param verifier the port a detection is asked through
     * @param executor the daemon executor a detection runs on, never the FX thread
     */
    @Inject
    public ProviderEngine(
            final SettingsViewModel viewModel,
            final ProviderForm form,
            final ProviderVerifier verifier,
            @BackgroundExecutor final ExecutorService executor) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.form = Objects.requireNonNull(form, "form");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.executor = Objects.requireNonNull(executor, "executor");
        offered = Bindings.createBooleanBinding(this::isOffered, detection, form.draft());
        lineShown = Bindings.createBooleanBinding(
                () -> !viewModel.adding().get() || detection.get() != null, detection, viewModel.adding());
        viewModel.editedProviderId().addListener((observed, was, now) -> reset("another provider"));
        viewModel.adding().addListener((observed, was, now) -> reset("adding changed"));
        viewModel.tests().lastReport().addListener((observed, was, now) -> onReport(now));
        form.saves().addListener((observed, was, now) -> detectSaved());
        variants = new VariantRemoval(viewModel, form);
    }

    /**
     * The {@code @variant} removed from an LM Studio model id, said once under the model entry.
     *
     * @return the variant without its {@code @}, or empty while none was removed for the open provider
     */
    public ReadOnlyStringProperty removedVariant() {
        return variants.removed();
    }

    /** The engine the last Save, Test or Detect again of the open provider found; {@code null} before any. */
    public ReadOnlyObjectProperty<@Nullable EngineDetection> detection() {
        return detection.getReadOnlyProperty();
    }

    /** Whether Detect again is waiting for the server. */
    public ReadOnlyBooleanProperty detecting() {
        return detecting.getReadOnlyProperty();
    }

    /** Whether the last Detect again got no answer; the line keeps its last text. */
    public ReadOnlyBooleanProperty unanswered() {
        return unanswered.getReadOnlyProperty();
    }

    /** Whether the address is an app that cannot translate a book, for which no engine may be named. */
    public ReadOnlyBooleanProperty unsuitable() {
        return unsuitable.getReadOnlyProperty();
    }

    /** Whether the offer of Ollama's own connection is shown: Ollama answers an OpenAI-compatible provider. */
    public BooleanBinding offered() {
        return offered;
    }

    /** Whether the detected-engine line is shown: for a saved provider always, for a new one once detected. */
    public BooleanBinding lineShown() {
        return lineShown;
    }

    /**
     * Asks the server again which engine it is; the person's choice goes, a declined offer stays declined. FX thread
     * only; returns at once.
     *
     * @return {@code true} if a detection started, {@code false} while one runs or the form holds something refused
     */
    public boolean detectAgain() {
        final Optional<TestSubject> subject = viewModel.testedSubject().get();
        log.debug("detect again pressed: detecting {}, subject present {}", detecting.get(), subject.isPresent());
        if (detecting.get() || subject.isEmpty()) {
            return false;
        }
        final ProviderConfig config = subject.get().config();
        log.info("engine detection asked again for provider {}", config.id());
        detecting.set(true);
        unanswered.set(false);
        final long started = ++epoch;
        submit(() -> verifier.detectEngine(config), result -> detectedAgain(result, started));
        return true;
    }

    /**
     * The person's "Treat this server as…" choice, kept in the draft until Save.
     *
     * @param chosen the engine, or {@code null} to follow the detected one
     */
    public void treatAs(final @Nullable ServerEngine chosen) {
        log.debug("treat this server as {}", chosen);
        form.edit(draft -> draft.withEngine(draft.engine().withChoice(chosen)));
    }

    /** Use it on the offer: Ollama's own connection at the same host, its {@code /v1} dropped; kept on Save. */
    public void useNative() {
        log.info("Ollama's own connection chosen on the offer");
        form.edit(draft -> draft.withKind(ProviderKind.OLLAMA).withAddress(rootOf(draft.address())));
    }

    /** Keep as is on the offer: only the offer is declined, so it never returns; kept on Save. */
    public void keepAsIs() {
        log.info("the OpenAI-compatible connection to Ollama kept on the offer");
        form.edit(draft -> draft.withEngine(draft.engine().declined()));
    }

    private static String rootOf(final String address) {
        return OPENAI_SUFFIX.matcher(address.strip()).replaceFirst("");
    }

    private boolean isOffered() {
        final EngineDetection found = detection.get();
        final ProviderDraft draft = form.draft().get();
        return found != null
                && found.offerNativeConnection()
                && draft.kind() == ProviderKind.OPENAI_COMPATIBLE
                && !draft.engine().offerDeclined();
    }

    private void reset(final String why) {
        epoch++;
        log.debug("engine state reset: {}", why);
        detection.set(null);
        detecting.set(false);
        unanswered.set(false);
        unsuitable.set(false);
    }

    // Save detects as Test does, keeping the person's choice; the connection check is a GET, never a model request.
    private void detectSaved() {
        final String id = viewModel.editedProviderId().get();
        if (viewModel.adding().get() || id.isEmpty()) {
            log.debug("no saved provider to detect the engine of");
            return;
        }
        log.debug("detecting the engine of saved provider {}", id);
        // Not a new epoch: a Detect again in flight must still land; another provider makes this one stale.
        final long started = epoch;
        submit(() -> verifier.verifyConnection(id), result -> {
            if (started == epoch && result.data() != null) {
                onReport(result.data());
            }
        });
    }

    private <T> void submit(final Supplier<Result<T>> call, final Consumer<Result<T>> then) {
        try {
            executor.execute(() -> {
                final Result<T> result = guarded(call);
                FxThread.run(() -> then.accept(result));
            });
        } catch (RuntimeException rejected) {
            log.error("the engine detection could not be submitted", rejected);
            detecting.set(false);
        }
    }

    private static <T> Result<T> guarded(final Supplier<Result<T>> call) {
        try {
            return call.get();
        } catch (RuntimeException thrown) {
            log.error("the verifier threw instead of returning a result", thrown);
            return Result.err(AppErrors.unexpected("The engine could not be detected.", thrown));
        }
    }

    private void detectedAgain(final Result<EngineDetection> result, final long started) {
        if (started != epoch) {
            log.debug("detection {} discarded: the current one is {}", started, epoch);
            return;
        }
        detecting.set(false);
        final EngineDetection found = result.data();
        if (found == null) {
            final AppError error = Objects.requireNonNull(result.error(), "error");
            log.info("detect again got no answer: {}", error.code());
            unanswered.set(true);
            return;
        }
        apply(found, true);
    }

    private void onReport(final @Nullable VerificationReport report) {
        if (report == null) {
            return;
        }
        final EngineDetection found = report.engine();
        if (found != null) {
            apply(found, false);
        } else if (refusedAsUnsuitable(report)) {
            log.debug("the connection test refused the address as an app that cannot translate a book");
            unsuitable.set(true);
        }
    }

    private static boolean refusedAsUnsuitable(final VerificationReport report) {
        return report.stages().stream()
                .filter(stage -> stage.stage() == VerificationStage.CONNECTION)
                .map(StageOutcome::error)
                .filter(Objects::nonNull)
                .map(error -> FailureReason.in(error.details()))
                .anyMatch(reason -> reason.filter(ProviderEngine::isUnsuitable).isPresent());
    }

    private static boolean isUnsuitable(final FailureReason reason) {
        return reason == FailureReason.NOT_SUITABLE_STRUCTURED || reason == FailureReason.NOT_SUITABLE_DOCUMENTS;
    }

    private void apply(final EngineDetection found, final boolean again) {
        final boolean suitable =
                found.suitability() == Suitability.SUITABLE || found.suitability() == Suitability.NOT_SUPPORTED_YET;
        log.debug(
                "engine found {} {} source {} suitability {} again {}",
                found.engine(),
                found.version(),
                found.source(),
                found.suitability(),
                again);
        detection.set(found);
        unsuitable.set(!suitable);
        if (found.source() != Source.DETECTED || found.suitability() != Suitability.SUITABLE) {
            if (again) {
                form.adoptEngine(engine -> engine.withChoice(null), false);
            }
            return;
        }
        final boolean stored = isStoredByRegistry();
        form.adoptEngine(
                engine -> again
                        ? engine.foundAgain(found.engine(), found.version())
                        : engine.found(found.engine(), found.version()),
                stored);
        if (stored) {
            viewModel.reloadProviders();
        }
    }

    // The registry keeps a detection on a saved provider whose kind and address are the ones probed.
    private boolean isStoredByRegistry() {
        final Optional<TestSubject> subject = viewModel.testedSubject().get();
        if (viewModel.adding().get() || subject.isEmpty()) {
            return false;
        }
        final ProviderConfig probed = subject.get().config();
        return viewModel
                .configOf(probed.id())
                .filter(saved ->
                        saved.kind() == probed.kind() && saved.baseUrl().equals(probed.baseUrl()))
                .isPresent();
    }
}
