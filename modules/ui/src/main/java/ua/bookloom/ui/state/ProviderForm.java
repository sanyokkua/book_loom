package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.EnvironmentVariables;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * What the Providers form holds between the first keystroke and Save: the draft, the saved baseline it is compared
 * with, the findings, and the saving and deleting that reach storage.
 *
 * <p>A singleton because the screen is rebuilt on every visit while unsaved typing must survive a question about
 * leaving. Every property is FX-thread only; every call into storage runs on the background executor, and its
 * outcome comes back through {@code Platform.runLater}.
 */
@Slf4j
@Singleton
public final class ProviderForm {

    /** A place on the form the screen is asked to bring into view. */
    public enum Spot {
        /** The Model field. */
        MODEL
    }

    private final SettingsViewModel viewModel;
    private final ProviderStorage storage;
    private final EnvironmentVariables environment;
    private final ExecutorService executor;
    private final ObjectProperty<ProviderDraft> draft =
            new SimpleObjectProperty<>(ProviderDraft.blank(ProviderKind.OLLAMA));
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<List<ProviderDraft.FormFinding>> findings =
            new ReadOnlyObjectWrapper<>(List.of());
    private final ReadOnlyObjectWrapper<ProviderDraft.CredentialStatus> credentialStatus =
            new ReadOnlyObjectWrapper<>(ProviderDraft.CredentialStatus.NONE);
    private final ProviderFoldState fold;
    private final ProviderFormFlags flags = new ProviderFormFlags();
    private final ProviderFormCurrent current;
    private final ReadOnlyBooleanWrapper canUseThis = new ReadOnlyBooleanWrapper(false);
    private ProviderDraft baseline = draft.get();

    // Set while the form itself changes the view model's model text, so the echo is not taken for typing.
    private boolean syncing;

    /**
     * Starts on whatever the view model has open and follows it.
     *
     * @param viewModel which provider is open, and the typed model
     * @param configs the registry a save and a delete go to, off the FX thread
     * @param modelSettings the per-model settings a delete removes, off the FX thread
     * @param settings where the current model is written and removed, off the FX thread
     * @param environment tells whether a sign-in variable has a value, never what it is
     * @param technical the switch that keeps the connection details open
     * @param executor the daemon executor every storage call runs on, never the FX thread
     */
    @Inject
    public ProviderForm(
            final SettingsViewModel viewModel,
            final ProviderConfigs configs,
            final ModelSettingsRepository modelSettings,
            final SettingsRepository settings,
            final EnvironmentVariables environment,
            final TechnicalDetails technical,
            @BackgroundExecutor final ExecutorService executor) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.storage = new ProviderStorage(
                Objects.requireNonNull(configs, "configs"),
                Objects.requireNonNull(modelSettings, "modelSettings"),
                Objects.requireNonNull(settings, "settings"));
        this.current = new ProviderFormCurrent(viewModel, storage, executor, flags);
        this.environment = Objects.requireNonNull(environment, "environment");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.fold = new ProviderFoldState(viewModel, Objects.requireNonNull(technical, "technical"));
        draft.addListener((observed, was, now) -> recompute());
        viewModel.model().addListener((observed, was, now) -> onSavedModelChanged(was, now));
        viewModel.editedProviderId().addListener((observed, was, now) -> loadDraft());
        viewModel.adding().addListener((observed, was, now) -> loadDraft());
        viewModel.draftModel().addListener((observed, was, now) -> onModelTyped(now));
        loadDraft();
    }

    /** The text the form holds now. */
    public ReadOnlyObjectProperty<ProviderDraft> draft() {
        return draft;
    }

    /** Whether the draft differs from what is saved. */
    public ReadOnlyBooleanProperty dirty() {
        return dirty.getReadOnlyProperty();
    }

    /** Whether a save or a delete is under way. */
    public ReadOnlyBooleanProperty saving() {
        return flags.saving.getReadOnlyProperty();
    }

    /** Whether the last save or delete failed in storage; the draft is untouched. */
    public ReadOnlyBooleanProperty saveFailed() {
        return flags.saveFailed.getReadOnlyProperty();
    }

    /** Whether the last thing that happened to the form was a successful save, until the next change. */
    public ReadOnlyBooleanProperty justSaved() {
        return flags.justSaved.getReadOnlyProperty();
    }

    /** Whether a refused save asked for the findings to be shown. */
    public ReadOnlyBooleanProperty errorsShown() {
        return flags.errorsShown.getReadOnlyProperty();
    }

    /** What is wrong or advisable about the draft. */
    public ReadOnlyObjectProperty<List<ProviderDraft.FormFinding>> findings() {
        return findings.getReadOnlyProperty();
    }

    /** Whether the sign-in's variable has a value; the value itself is never held. */
    public ReadOnlyObjectProperty<ProviderDraft.CredentialStatus> credentialStatus() {
        return credentialStatus.getReadOnlyProperty();
    }

    /** Whether the connection details are open: while adding, after a failed check, while technical details are on, or when asked for. */
    public BooleanBinding foldOpen() {
        return fold.open();
    }

    /** The place the screen was last asked to bring into view. */
    public ReadOnlyObjectProperty<@Nullable Spot> focusRequest() {
        return fold.focusRequest();
    }

    /**
     * Opens the connection details and asks for a place in them to be shown.
     *
     * @param spot the place
     */
    public void openFoldAt(final Spot spot) {
        Objects.requireNonNull(spot, "spot");
        fold.openAt(spot);
    }

    /**
     * Changes the draft.
     *
     * @param change gets the draft and returns the changed one
     */
    public void edit(final UnaryOperator<ProviderDraft> change) {
        Objects.requireNonNull(change, "change");
        final ProviderDraft next = change.apply(draft.get());
        if (next.equals(draft.get())) {
            return;
        }
        draft.set(next);
        flags.clearFeedback();
        showModel(next.model());
    }

    // The view model's model text follows the draft without the echo being taken for typing.
    private void showModel(final String text) {
        if (!text.equals(viewModel.draftModel().get())) {
            syncing = true;
            try {
                viewModel.draftModel().set(text);
            } finally {
                syncing = false;
            }
        }
    }

    /**
     * The models to offer: those the server listed, then the ones the person added, each once.
     *
     * @return the names in that order
     */
    public List<String> modelChoices() {
        return draft.get().modelChoices(viewModel.modelListing().offered());
    }

    /** The typed name of the provider the unsaved changes belong to; blank while none is typed. */
    public String subject() {
        return draft.get().name().strip();
    }

    /**
     * Saves the draft, then runs {@code then}. A draft the rules refuse is not stored and does not run it; so does a
     * failure in storage, which also leaves the draft as typed.
     *
     * @param then what to do once the draft is stored and the list has been read again
     */
    public void save(final Runnable then) {
        Objects.requireNonNull(then, "then");
        if (flags.saving.get()) {
            log.debug("save ignored: one is under way");
            return;
        }
        final ProviderDraft typed = draft.get();
        final boolean adding = viewModel.adding().get();
        final String id = ProviderDrafts.idToSave(viewModel, typed, adding);
        final Result<ProviderConfig> built = id.isEmpty() ? null : typed.toConfig(id);
        if (built == null || built.isErr() || findings.get().stream().anyMatch(ProviderDraft.FormFinding::blocking)) {
            flags.errorsShown.set(true);
            log.debug("save of '{}' refused: {} finding(s)", id, findings.get().size());
            return;
        }
        final boolean current =
                !adding && id.equals(viewModel.selectedProviderId().get());
        final ProviderConfig config = Objects.requireNonNull(built.data(), "data");
        log.debug("saving '{}': adding {}, current {}", id, adding, current);
        flags.saving.set(true);
        flags.saveFailed.set(false);
        executor.execute(() -> {
            final Result<Boolean> stored =
                    storage.persist(config, current ? typed.model().strip() : null);
            onFx(() -> saved(stored, new ProviderSaved(id, adding, current, typed), then));
        });
    }

    /** Drops the typing: a new provider is abandoned, an existing one shows what is saved. */
    public void discard() {
        log.debug("discard pressed, adding {}", viewModel.adding().get());
        if (viewModel.adding().get()) {
            viewModel.cancelAdding();
            // With no provider to go back to the form stays on a blank draft, which must not count as typing.
            if (viewModel.adding().get() || viewModel.editedProviderId().get().isEmpty()) {
                setBaseline(ProviderDraft.blank(ProviderKind.OLLAMA));
            }
            return;
        }
        setDraft(baseline);
        showModel(baseline.model());
    }

    /**
     * Deletes a provider with its per-model settings; when it was the current one, the current model goes too.
     *
     * @param id the provider to delete
     * @param then what to do once it is gone and the list has been read again; not run when storage failed
     */
    public void delete(final String id, final Runnable then) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(then, "then");
        current.delete(id, then);
    }

    /** Whether Use this one may be pressed: a saved provider is open and only its model was typed. */
    public ReadOnlyBooleanProperty canUseThis() {
        return canUseThis.getReadOnlyProperty();
    }

    /** Makes the open provider and its typed model current; ignored unless {@link #canUseThis()} and not saving. */
    public void useThis(final Runnable then) {
        Objects.requireNonNull(then, "then");
        useThis(viewModel.editedProviderId().get(), draft.get().model().strip(), then);
    }

    /** Makes a provider chosen earlier current even if another row was opened since. */
    public void useThis(final String providerId, final String model, final Runnable then) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(then, "then");
        final boolean stillOpen = providerId.equals(viewModel.editedProviderId().get());
        if (flags.saving.get() || (stillOpen && !canUseThis.get())) {
            log.debug("use this one ignored: saving {}, open {}", flags.saving.get(), stillOpen);
            return;
        }
        log.info("making '{}' current", providerId);
        current.use(new ModelSelection(providerId, model), stillOpen ? () -> keepModel(model) : () -> {}, then);
    }

    // What was typed for the model is now what is stored, so the form is clean.
    private void keepModel(final String model) {
        baseline = baseline.withModel(model);
        draft.set(draft.get().withModel(model));
        showModel(model);
        recompute();
    }

    private void saved(final Result<Boolean> stored, final ProviderSaved done, final Runnable then) {
        if (stored.isErr()) {
            flags.failed("provider '" + done.id() + "' was not saved");
            return;
        }
        log.info("provider '{}' saved", done.id());
        viewModel.reloadProviders(() -> {
            if (done.current()) {
                viewModel.applySavedCurrentModel(done.typed().model());
            }
            if (done.adding()) {
                viewModel.editProvider(done.id());
                viewModel.draftModel().set(done.typed().model());
            }
            showStored(done);
            flags.saving.set(false);
            flags.justSaved.set(!dirty.get());
            then.run();
        });
    }

    // What the registry now holds is the baseline, and the form shows it, so stray spaces typed in a field do not
    // leave the form dirty after a good save.
    private void showStored(final ProviderSaved done) {
        final String model =
                done.current() ? done.typed().model().strip() : done.typed().model();
        final ProviderDraft stored = ProviderDrafts.stored(viewModel, done.id(), model, done.typed());
        baseline = stored;
        draft.set(stored);
        showModel(model);
        recompute();
    }

    private void loadDraft() {
        ProviderDrafts.open(viewModel)
                .ifPresentOrElse(
                        this::setBaseline,
                        () -> log.debug(
                                "no provider '{}' to load into the form",
                                viewModel.editedProviderId().get()));
    }

    private void setBaseline(final ProviderDraft next) {
        baseline = next;
        flags.clearFeedback();
        fold.reset();
        setDraft(next);
        recompute();
    }

    private void setDraft(final ProviderDraft next) {
        draft.set(next);
        flags.clearFeedback();
        recompute();
    }

    private void onModelTyped(final String text) {
        if (!syncing && !text.equals(draft.get().model())) {
            draft.set(draft.get().withModel(text));
        }
    }

    // The current model changed from outside (the first-launch look, a save): the open form follows it when it is the
    // current provider's, and whatever else was typed stays.
    private void onSavedModelChanged(final String was, final String now) {
        final boolean current = !viewModel.adding().get()
                && viewModel
                        .editedProviderId()
                        .get()
                        .equals(viewModel.selectedProviderId().get());
        if (current && baseline.model().equals(was)) {
            final boolean untouched = draft.get().model().equals(was);
            baseline = baseline.withModel(now);
            if (untouched) {
                draft.set(draft.get().withModel(now));
                showModel(now);
            }
            recompute();
        }
    }

    private void recompute() {
        final ProviderDraft now = draft.get();
        dirty.set(!now.equals(baseline));
        viewModel.formDirty(dirty.get());
        findings.set(ProviderDrafts.findings(viewModel, now));
        credentialStatus.set(now.credentialStatus(environment));
        canUseThis.set(ProviderDrafts.canUse(viewModel, now, baseline));
        ProviderDrafts.publishSubject(viewModel, draft::get, () -> baseline);
    }

    private static void onFx(final Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
