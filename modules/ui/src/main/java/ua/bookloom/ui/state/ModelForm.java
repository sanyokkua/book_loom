package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.IoExecutor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What the Models tab holds between choosing a model and Save: the chosen model, the draft and the saved baseline it
 * is compared with, what the program says about the model, and the settings the model refused.
 *
 * <p>A singleton because the screen is rebuilt on every visit while unsaved typing must survive a question about
 * leaving. Every property is FX-thread only; the store and the provider are called on the background executor and
 * their outcome comes back through {@code Platform.runLater}. Save and Send again are {@code update}s, so a refusal
 * the chat layer remembers while the tab is open is never overwritten, and Send again leaves the draft alone.
 */
@Slf4j
@Singleton
public final class ModelForm extends ModelFormProperties {

    private final ModelSettingsRepository store;
    private final ModelCapabilities capabilities;
    private final StructuredAnswers structured;
    private final SettingsViewModel viewModel;
    private final ExecutorService background;
    private final ExecutorService io;
    private final Messages messages;
    private ModelDraft baseline = draft.get();

    // Counts the models chosen; a load that arrives after another choice is dropped. FX thread only.
    private long epoch;

    /**
     * Creates the form and follows the roster: the first time models are listed, one is chosen.
     *
     * @param store where the per-model settings are kept
     * @param capabilities what the providers say about a model
     * @param structured whether a model answered in the structured form this session
     * @param roster the models on offer
     * @param viewModel names the current model, which is chosen first
     * @param messages the catalogue the unsaved question's model name is worded from
     * @param background the daemon executor the store is read and written on, never the FX thread
     * @param io the executor the provider is asked on
     */
    @Inject
    public ModelForm(
            final ModelSettingsRepository store,
            final ModelCapabilities capabilities,
            final StructuredAnswers structured,
            final ModelRoster roster,
            final SettingsViewModel viewModel,
            final Messages messages,
            @BackgroundExecutor final ExecutorService background,
            @IoExecutor final ExecutorService io) {
        this.store = Objects.requireNonNull(store, "store");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.structured = Objects.requireNonNull(structured, "structured");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.background = Objects.requireNonNull(background, "background");
        this.io = Objects.requireNonNull(io, "io");
        Objects.requireNonNull(roster, "roster").entries().addListener((ListChangeListener<ModelEntry>)
                change -> followOffer(roster.entries()));
        structured.revision().addListener(observed -> followStructured());
    }

    /**
     * Shows another model's settings, dropping the draft of the one left. Choosing the model already shown changes
     * nothing, so unsaved typing survives coming back to the tab. Returns at once; the settings and the program's
     * answer arrive on the FX thread.
     *
     * @param entry the model to show
     */
    public void select(final ModelEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.equals(selected.get())) {
            log.debug("model {} is already shown", entry.modelId());
            return;
        }
        final long mine = ++epoch;
        log.debug("choosing model {} of {}, reading #{}", entry.modelId(), entry.providerId(), mine);
        selected.set(entry);
        baseline = ModelDraft.of(defaultsOf(entry));
        show(baseline);
        refused.clear();
        clearFeedback();
        facts.set(new ModelFacts(null, null, structuredOf(entry), true));
        reading.set(true);
        background.execute(() -> loadStored(entry, mine));
    }

    /**
     * Changes the draft.
     *
     * @param change gets the draft and returns the changed one
     */
    public void edit(final UnaryOperator<ModelDraft> change) {
        Objects.requireNonNull(change, "change");
        show(change.apply(draft.get()));
        clearFeedback();
    }

    /**
     * Turns the context window on or off; turning it on starts from the loaded figure when there is one.
     *
     * @param on whether the window is set
     */
    public void switchContext(final boolean on) {
        edit(current ->
                current.withContext(on, current.contextText(), facts.get().loaded()));
    }

    /**
     * Saves the draft over what is stored, then runs {@code then}. A draft with findings is not stored and does not
     * run it; so does a failure in storage, which also leaves the draft as typed.
     *
     * @param then what to do once the settings are stored
     */
    public void save(final Runnable then) {
        Objects.requireNonNull(then, "then");
        final @Nullable ModelEntry entry = selected.get();
        if (entry == null || saving.get()) {
            log.debug("save ignored: model chosen {}, saving {}", entry != null, saving.get());
            return;
        }
        final ModelDraft typed = draft.get();
        if (!typed.findings().isEmpty()) {
            errorsShown.set(true);
            log.debug(
                    "save of {} refused: {} finding(s)",
                    entry.modelId(),
                    typed.findings().size());
            return;
        }
        saving.set(true);
        saveFailed.set(false);
        final long mine = epoch;
        background.execute(() -> {
            final Result<ModelSettings> stored = store.update(
                    entry.providerId(),
                    entry.modelId(),
                    current -> Objects.requireNonNull(typed.apply(current).data(), "validated draft"));
            Platform.runLater(() -> saved(entry, mine, stored, then));
        });
    }

    /** Drops the typing, so the form shows what is saved. */
    public void discard() {
        log.debug("discard pressed");
        show(baseline);
        clearFeedback();
    }

    /**
     * Forgets one refusal at once: the next request to the model carries the setting again. The draft, the baseline
     * and whether the form is dirty stay as they are.
     *
     * @param name the refused setting
     */
    public void sendAgain(final String name) {
        Objects.requireNonNull(name, "name");
        final @Nullable ModelEntry entry = selected.get();
        if (entry == null || sendingAgain.get()) {
            log.debug(
                    "send again of '{}' ignored: model chosen {}, under way {}",
                    name,
                    entry != null,
                    sendingAgain.get());
            return;
        }
        log.debug("send again of '{}' for {}", name, entry.modelId());
        sendingAgain.set(true);
        refusedFailed.set(false);
        final long mine = epoch;
        background.execute(() -> {
            final Result<ModelSettings> kept =
                    store.update(entry.providerId(), entry.modelId(), current -> current.withoutRefusal(name));
            Platform.runLater(() -> forgotten(mine, kept));
        });
    }

    /**
     * The name the unsaved changes belong to.
     *
     * @return the model and its provider, or an empty string while no model is chosen
     */
    public String subject() {
        final @Nullable ModelEntry entry = selected.get();
        return entry == null
                ? ""
                : messages.get(MessageKey.SETTINGS_MODELS_ENTRY, entry.modelId(), entry.providerName());
    }

    // The stored settings come first and enable the form; what the program says follows without holding it up.
    private void loadStored(final ModelEntry entry, final long mine) {
        final Result<Optional<ModelSettings>> found = store.find(entry.providerId(), entry.modelId());
        if (found.isErr()) {
            log.warn(
                    "settings of {} unreadable: {}",
                    entry.modelId(),
                    Objects.requireNonNull(found.error()).code());
        }
        final ModelSettings settings =
                found.isOk() ? Objects.requireNonNull(found.data()).orElse(defaultsOf(entry)) : defaultsOf(entry);
        Platform.runLater(() -> stored(entry, mine, settings));
    }

    private void stored(final ModelEntry entry, final long mine, final ModelSettings settings) {
        if (mine != epoch) {
            log.debug("reading #{} of {} dropped: the latest is #{}", mine, entry.modelId(), epoch);
            return;
        }
        baseline = ModelDraft.of(settings);
        show(baseline);
        refused.setAll(settings.refused());
        reading.set(false);
        log.debug("stored settings of {} shown", entry.modelId());
        io.execute(() -> askReport(entry, mine));
    }

    private void askReport(final ModelEntry entry, final long mine) {
        final Result<ModelReport> asked = capabilities.report(entry.providerId(), entry.modelId());
        final ModelReport report = asked.isOk() ? Objects.requireNonNull(asked.data()) : new ModelReport(null, null);
        Platform.runLater(() -> reported(entry, mine, report));
    }

    private void reported(final ModelEntry entry, final long mine, final ModelReport report) {
        if (mine != epoch) {
            log.debug("report #{} of {} dropped: the latest is #{}", mine, entry.modelId(), epoch);
            return;
        }
        facts.set(new ModelFacts(report.contextLength(), report.loadedContext(), structuredOf(entry), false));
        log.debug(
                "model {} reported: context {}, loaded {}",
                entry.modelId(),
                report.contextLength(),
                report.loadedContext());
    }

    private void saved(
            final ModelEntry entry, final long mine, final Result<ModelSettings> stored, final Runnable then) {
        saving.set(false);
        if (stored.isErr()) {
            saveFailed.set(true);
            log.warn(
                    "settings of {} not saved: {}",
                    entry.modelId(),
                    Objects.requireNonNull(stored.error()).code());
            return;
        }
        final ModelSettings kept = Objects.requireNonNull(stored.data(), "data");
        log.info("settings of {} saved", entry.modelId());
        if (mine == epoch) {
            baseline = ModelDraft.of(kept);
            show(baseline);
            refused.setAll(kept.refused());
            justSaved.set(true);
        }
        then.run();
    }

    private void forgotten(final long mine, final Result<ModelSettings> kept) {
        sendingAgain.set(false);
        if (kept.isErr()) {
            refusedFailed.set(true);
            log.warn(
                    "a refusal was not forgotten: {}",
                    Objects.requireNonNull(kept.error()).code());
        } else {
            log.info("a refusal was forgotten: the next request carries the setting again");
            if (mine == epoch) {
                refused.setAll(Objects.requireNonNull(kept.data(), "data").refused());
            }
        }
    }

    // A new offer keeps the chosen model while it is on it; else the model that was chosen is gone (its provider was
    // deleted, say) and the first one is shown, or nothing when the offer is empty, so Save cannot store an orphan.
    private void followOffer(final List<ModelEntry> offered) {
        final @Nullable ModelEntry shown = selected.get();
        if (shown != null) {
            final int at = offered.indexOf(shown);
            if (at >= 0) {
                selected.set(offered.get(at));
                return;
            }
            log.debug("model {} of {} is no longer offered", shown.modelId(), shown.providerId());
        }
        if (offered.isEmpty()) {
            clearSelection();
        } else {
            chooseFirst(offered);
        }
    }

    private void chooseFirst(final List<ModelEntry> offered) {
        final String provider = viewModel.selectedProviderId().get();
        final String model = viewModel.model().get().strip();
        final ModelEntry choice = offered.stream()
                .filter(entry ->
                        entry.providerId().equals(provider) && entry.modelId().equals(model))
                .findFirst()
                .orElse(offered.getFirst());
        log.debug("first choice: {} of {}", choice.modelId(), choice.providerId());
        select(choice);
    }

    private void clearSelection() {
        if (selected.get() == null) {
            return;
        }
        epoch++;
        selected.set(null);
        baseline = ModelDraft.of(ModelSettings.defaults("", ""));
        show(baseline);
        refused.clear();
        clearFeedback();
        facts.set(ModelFacts.NONE);
        reading.set(false);
    }

    private void followStructured() {
        final @Nullable ModelEntry entry = selected.get();
        if (entry != null) {
            final ModelFacts now = facts.get();
            facts.set(new ModelFacts(now.contextLength(), now.loaded(), structuredOf(entry), now.loading()));
        }
    }

    private @Nullable Boolean structuredOf(final ModelEntry entry) {
        return structured.lookup(entry.providerId(), entry.modelId()).orElse(null);
    }

    private static ModelSettings defaultsOf(final ModelEntry entry) {
        return ModelSettings.defaults(entry.providerId(), entry.modelId());
    }

    private void clearFeedback() {
        errorsShown.set(false);
        saveFailed.set(false);
        justSaved.set(false);
    }

    // Set and judged together: an equal draft raises no change event, yet the baseline may just have moved.
    private void show(final ModelDraft next) {
        draft.set(next);
        recompute();
    }

    private void recompute() {
        dirty.set(!draft.get().equals(baseline));
        findings.set(draft.get().findings());
    }
}
