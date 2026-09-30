package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

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
    private final ObservableList<ProviderRow> providers;
    private final ReadOnlyStringWrapper selectedProviderId = new ReadOnlyStringWrapper("");
    private final StringProperty model = new SimpleStringProperty("");

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
        this.modelListing = Objects.requireNonNull(modelListing, "modelListing");
        this.tests = new ProviderTestRunner(
                Objects.requireNonNull(verifier, "verifier"),
                Objects.requireNonNull(toasts, "toasts"),
                Objects.requireNonNull(errors, "errors"),
                Objects.requireNonNull(executor, "executor"),
                selectedProviderId.getReadOnlyProperty(),
                model);
        this.providers = FXCollections.unmodifiableObservableList(FXCollections.observableArrayList(rowsOf(configs)));
        providers.stream().findFirst().ifPresent(first -> selectedProviderId.set(first.id()));
        log.info("settings ready: {} provider(s), selected '{}'", providers.size(), selectedProviderId.get());
    }

    private static List<ProviderRow> rowsOf(final ProviderConfigs configs) {
        return configs.all().stream()
                .sorted(Comparator.comparing(ProviderConfig::kind).thenComparing(ProviderConfig::id))
                .map(SettingsViewModel::rowOf)
                .toList();
    }

    private static ProviderRow rowOf(final ProviderConfig config) {
        final URI url = config.baseUrl();
        final String hostPort = url.getPort() < 0 ? url.getHost() : url.getHost() + ":" + url.getPort();
        return new ProviderRow(config.id(), config.kind(), url.toString(), hostPort);
    }

    /**
     * The three provider tests and the findings of the last one.
     *
     * @return the runner over this view model's selected provider and model
     */
    public ProviderTestRunner tests() {
        return tests;
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
}
