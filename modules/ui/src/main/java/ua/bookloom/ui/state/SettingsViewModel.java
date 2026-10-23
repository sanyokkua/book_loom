package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * What the settings screen shows and does about providers: which one is selected, which model is chosen for it, and
 * the tests run against the pair (see {@link ProviderTestRunner}).
 *
 * <p>A singleton because the screen's controller is rebuilt on every visit while the choice must survive it.
 * Everything that touches a property runs on the FX Application Thread, which the methods below state.
 */
@Slf4j
@Singleton
public final class SettingsViewModel {

    private final ModelListing modelListing;
    private final ProviderTestRunner tests;
    private final ProviderConfigs configs;
    private final SettingsRepository settingsStore;
    private final ExecutorService executor;
    private final ObservableList<ProviderRow> loaded = FXCollections.observableArrayList();
    private final ObservableList<ProviderRow> providers = FXCollections.unmodifiableObservableList(loaded);
    private final Map<String, ProviderConfig> snapshot = new LinkedHashMap<>();
    private final Messages messages;
    private final ReadOnlyStringWrapper selectedProviderId = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper editedProviderId = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper adding = new ReadOnlyBooleanWrapper(false);
    private final StringProperty model = new SimpleStringProperty("");
    private final StringProperty draftModel = new SimpleStringProperty("");
    private final ProviderTestTarget testTarget = new ProviderTestTarget();
    private final ModelNotesFollower modelNotes;

    // Only the first read may make the first row current (a deleted current provider stays deleted); a dirty form
    // holds typing that an adopted model must not replace.
    private boolean loadedOnce;
    private boolean formDirty;

    /**
     * Starts reading the registry on the background executor; the list stays empty, and nothing is selected, until the
     * read is published. The first provider in {@link ua.bookloom.api.llm.ProviderKind} order is then selected.
     *
     * @param configs the registry the rows are read from; the order it lists in is not relied on
     * @param verifier the port a check is run through
     * @param modelListing the models the selected provider offers; this view model discards and re-asks it when the
     *     provider changes
     * @param errors where a refusal of the whole check is shown
     * @param executor the daemon executor the verifier runs on, never the FX thread
     * @param activities the work under way, which a provider test registers with and the inference test must not
     *     overlap
     * @param settingsStore where the model chosen last time is read from, on the background executor
     * @param messages the catalogue a provider without a name of its own is named from
     * @param speedRecorder where the seconds per segment a passing inference test measured are kept
     */
    @Inject
    public SettingsViewModel(
            final ProviderConfigs configs,
            final ProviderVerifier verifier,
            final ModelListing modelListing,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor,
            final ActivityTracker activities,
            final SettingsRepository settingsStore,
            final Messages messages,
            final ModelSpeedRecorder speedRecorder) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
        this.modelListing = Objects.requireNonNull(modelListing, "modelListing");
        this.tests = new ProviderTestRunner(
                Objects.requireNonNull(verifier, "verifier"),
                Objects.requireNonNull(errors, "errors"),
                Objects.requireNonNull(executor, "executor"),
                testTarget.subject(),
                Objects.requireNonNull(activities, "activities"),
                Objects.requireNonNull(speedRecorder, "speedRecorder"));
        this.modelNotes = new ModelNotesFollower(editedProviderId, draftModel, providers);
        this.executor = executor;
        executor.execute(() -> loadProviders(readCurrentModel(), () -> {}));
    }

    /** Reads the registry off the FX thread: storage is never touched where the window draws. */
    private void loadProviders(final Optional<ModelSelection> stored, final Runnable afterPublished) {
        log.debug("loading the providers on {}", Thread.currentThread().getName());
        final List<ProviderConfig> read = ProviderRows.sorted(configs);
        if (Platform.isFxApplicationThread()) {
            publishProviders(read, stored, afterPublished);
        } else {
            Platform.runLater(() -> publishProviders(read, stored, afterPublished));
        }
    }

    private Optional<ModelSelection> readCurrentModel() {
        final Result<Optional<ModelSelection>> read = settingsStore.get(AppSetting.CURRENT_MODEL);
        if (read.isErr()) {
            log.warn(
                    "the stored model could not be read: {}",
                    Objects.requireNonNull(read.error(), "error").code());
            return Optional.empty();
        }
        return Objects.requireNonNull(read.data(), "data");
    }

    private void publishProviders(
            final List<ProviderConfig> read, final Optional<ModelSelection> stored, final Runnable afterPublished) {
        snapshot.clear();
        read.forEach(config -> snapshot.put(config.id(), config));
        final List<ProviderRow> rows = read.stream().map(ProviderRows::rowOf).toList();
        loaded.setAll(rows);
        if (!loadedOnce && selectedProviderId.get().isEmpty()) {
            rows.stream().findFirst().ifPresent(first -> selectedProviderId.set(first.id()));
        }
        loadedOnce = true;
        repairEdited();
        stored.ifPresent(this::adopt);
        log.info("settings ready: {} provider(s), selected '{}'", rows.size(), selectedProviderId.get());
        afterPublished.run();
    }

    // The edited provider is always a listed one: a first load starts on the current provider, and a deleted one
    // gives way to it.
    private void repairEdited() {
        final String edited = editedProviderId.get();
        if (!adding.get() && (edited.isEmpty() || !snapshot.containsKey(edited))) {
            final String current = selectedProviderId.get();
            final String target = !current.isEmpty()
                    ? current
                    : loaded.isEmpty() ? "" : loaded.getFirst().id();
            log.debug("edited provider '{}' replaced by '{}'", edited, target);
            showEdited(target);
        }
    }

    private void showEdited(final String id) {
        draftModel.set(id.equals(selectedProviderId.get()) ? model.get() : "");
        editedProviderId.set(id);
    }

    /**
     * Reads the registry again, on the background executor, and then runs {@code afterPublished} on the FX thread, so
     * a caller that has just saved a provider can adopt it once its row exists. FX thread only.
     *
     * @param afterPublished what to run once the rows are published
     */
    void reloadProviders(final Runnable afterPublished) {
        Objects.requireNonNull(afterPublished, "afterPublished");
        log.debug("providers reload requested");
        executor.execute(() -> loadProviders(Optional.empty(), afterPublished));
    }

    /** Reads the registry again, on the background executor, and publishes the rows. FX thread only. */
    public void reloadProviders() {
        reloadProviders(() -> {});
    }

    /**
     * Makes a provider and model the current ones, as if the person had picked them in Settings.
     *
     * @param chosen the provider and model; ignored when no row carries the provider
     */
    public void adopt(final ModelSelection chosen) {
        Objects.requireNonNull(chosen, "chosen");
        if (providers.stream().noneMatch(row -> row.id().equals(chosen.providerId()))) {
            log.debug("adopt ignored: no provider '{}' is listed", chosen.providerId());
            return;
        }
        final boolean following =
                !adding.get() && !formDirty && editedProviderId.get().equals(selectedProviderId.get());
        selectedProviderId.set(chosen.providerId());
        model.set(chosen.modelId());
        if (following) {
            draftModel.set(chosen.modelId());
            editedProviderId.set(chosen.providerId());
        }
        log.info("model adopted: {}/{}", chosen.providerId(), chosen.modelId());
    }

    /** The three provider tests and the findings of the last one. */
    public ProviderTestRunner tests() {
        return tests;
    }

    /** The providers on offer, in the order they are listed. */
    public ObservableList<ProviderRow> providers() {
        return providers;
    }

    /** The id of the current provider, the one runs and checks of other screens use. */
    public ReadOnlyStringProperty selectedProviderId() {
        return selectedProviderId.getReadOnlyProperty();
    }

    /** The model of the current provider, as last saved or adopted. Typing in the form changes {@link #draftModel()} only. */
    public StringProperty model() {
        return model;
    }

    /** The id of the provider whose form is open. */
    public ReadOnlyStringProperty editedProviderId() {
        return editedProviderId.getReadOnlyProperty();
    }

    /** Whether the form is for a provider that is not stored yet. */
    public ReadOnlyBooleanProperty adding() {
        return adding.getReadOnlyProperty();
    }

    /** The model text of the open form, as typed or picked; a different provider resets it. A check asks about the text stripped of surrounding whitespace. */
    public StringProperty draftModel() {
        return draftModel;
    }

    /** What a test would be run against, kept up to date by the form; empty while the form holds something refused. */
    ObjectProperty<Optional<TestSubject>> testSubject() {
        return testTarget.subject();
    }

    /** What a test would be run against now; changes when the form is edited, so a view can follow a rename. */
    public ReadOnlyObjectProperty<Optional<TestSubject>> testedSubject() {
        return testTarget.subject();
    }

    /** The name the tested provider is shown by: the form's name as typed, else the open provider's. */
    public String testedName() {
        return testTarget.subject().get().map(TestSubject::name).orElseGet(() -> providerName(editedProviderId.get()));
    }

    /** Whether the open form holds something the rules refuse, so no test can be run until it is fixed. */
    public ReadOnlyBooleanProperty testsBlocked() {
        return testTarget.blocked();
    }

    /** Takes what the form now offers to test, and whether the draft is what stops a test. */
    void publishTestSubject(final Optional<TestSubject> subject, final boolean blocked) {
        testTarget.publish(subject, blocked);
    }

    /** The advice for the selected provider and the chosen model, which follows both. */
    public ReadOnlyObjectProperty<List<ModelAdvice.Note>> modelNotes() {
        return modelNotes.notes();
    }

    /**
     * The models the selected provider offers. Typing a name never depends on it.
     *
     * @return the listing this view model discards and re-asks whenever another provider is selected
     */
    public ModelListing modelListing() {
        return modelListing;
    }

    /** Asks the server the open form describes for its models again, with the values as typed. FX thread only. */
    public void refreshModels() {
        log.debug("model list refresh requested for '{}'", editedProviderId.get());
        testTarget.subject().get().ifPresent(subject -> modelListing.refresh(subject.config()));
    }

    /**
     * Takes the model entry's text as it stands, unstripped and unchecked, so a check is available as soon as something
     * is typed (a disabled button never takes the click that would commit the entry); blank means none. FX thread only.
     */
    public void setModelText(final String text) {
        Objects.requireNonNull(text, "text");
        draftModel.set(text);
    }

    /** Notes that the person settled on the entry (Enter, or a pick from the list); the text itself already counts. */
    public void commitModel() {
        final String chosen = draftModel.get().strip();
        if (chosen.isEmpty()) {
            log.debug("model entry committed while blank");
        } else {
            log.info("model chosen: {}", chosen);
        }
    }

    /**
     * Opens a provider's form; a different one also discards the typed model, the offered models and the last
     * findings, which meant something only for the server they came from, then asks the new provider for its models.
     * The current provider does not change. FX thread only.
     *
     * @param id a provider id; an id no row carries is ignored
     */
    public void editProvider(final String id) {
        Objects.requireNonNull(id, "id");
        log.debug("edit provider '{}' requested", id);
        if (!snapshot.containsKey(id)) {
            log.debug("edit ignored: no provider '{}' is listed", id);
        } else if (!adding.get() && id.equals(editedProviderId.get())) {
            log.debug("edit ignored: '{}' is already open", id);
        } else {
            adding.set(false);
            showEdited(id);
            modelListing.discard();
            modelListing.refresh(id);
            log.info("provider opened for editing: {}", id);
        }
    }

    /** Opens a blank form for a new provider; nothing is stored until it is saved. FX thread only. */
    public void startAdding() {
        log.info("adding a provider");
        adding.set(true);
        draftModel.set("");
        editedProviderId.set("");
        modelListing.discard();
    }

    /** Leaves the blank form and opens the current provider again. FX thread only. */
    public void cancelAdding() {
        log.debug("adding cancelled");
        final String current = selectedProviderId.get();
        if (current.isEmpty()) {
            adding.set(false);
        } else {
            editProvider(current);
        }
    }

    /** Says whether the open form holds typing, which an adopted model leaves alone. */
    void formDirty(final boolean dirty) {
        formDirty = dirty;
    }

    /** Forgets the current provider and model, after the current provider was deleted. FX thread only. */
    public void forgetCurrent() {
        log.info("the current provider and model are forgotten");
        selectedProviderId.set("");
        model.set("");
    }

    /** Makes a saved model text (stripped; blank means none) the current model of the current provider. FX thread only. */
    public void applySavedCurrentModel(final String text) {
        Objects.requireNonNull(text, "text");
        model.set(text.strip());
        log.debug("current model text now has {} character(s)", model.get().length());
    }

    /** Names a provider for display: its own name, else the catalogue's, else its id. */
    public String providerName(final String id) {
        Objects.requireNonNull(id, "id");
        return providers.stream()
                .filter(row -> row.id().equals(id))
                .findFirst()
                .map(row -> ProviderNames.displayName(messages, row))
                .orElseGet(() -> ProviderNames.displayName(messages, id));
    }

    /** The stored description of a listed provider as of the last read, or empty if no row carries the id. */
    public Optional<ProviderConfig> configOf(final String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(snapshot.get(id));
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
}
