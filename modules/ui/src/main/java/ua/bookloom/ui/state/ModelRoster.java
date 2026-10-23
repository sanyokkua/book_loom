package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.IoExecutor;

/**
 * Every model the Models tab can show, across all providers.
 *
 * <p>A singleton because the screen is rebuilt on every visit while the list should survive it. Properties are
 * FX-thread only; the stores are read on the background executor and each provider is listed on the I/O executor.
 */
@Slf4j
@Singleton
public final class ModelRoster {

    private final ObservableList<ModelEntry> entries = FXCollections.observableArrayList();
    private final ObservableList<ModelEntry> entriesView = FXCollections.unmodifiableObservableList(entries);
    private final ReadOnlyBooleanWrapper listing = new ReadOnlyBooleanWrapper(false);
    private final ProviderConfigs configs;
    private final ModelCatalog catalog;
    private final ModelSettingsRepository modelSettings;
    private final SettingsViewModel viewModel;
    private final ExecutorService background;
    private final ExecutorService io;

    // Counts the readings started; one that is no longer the latest when it arrives is dropped. FX thread only.
    private long epoch;

    /**
     * Creates the roster.
     *
     * @param configs the registry whose providers are listed
     * @param catalog the port each provider's models are asked from
     * @param modelSettings the per-model settings, whose models are shown even when no server lists them
     * @param viewModel names the providers
     * @param background the daemon executor the stores are read on
     * @param io the executor the provider listings run on
     */
    @Inject
    public ModelRoster(
            final ProviderConfigs configs,
            final ModelCatalog catalog,
            final ModelSettingsRepository modelSettings,
            final SettingsViewModel viewModel,
            @BackgroundExecutor final ExecutorService background,
            @IoExecutor final ExecutorService io) {
        this.configs = Objects.requireNonNull(configs, "configs");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.modelSettings = Objects.requireNonNull(modelSettings, "modelSettings");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.background = Objects.requireNonNull(background, "background");
        this.io = Objects.requireNonNull(io, "io");
    }

    /** The models, provider by provider; changes only on the FX thread. */
    public ObservableList<ModelEntry> entries() {
        return entriesView;
    }

    /** Whether a reading of the providers is under way. */
    public ReadOnlyBooleanProperty listing() {
        return listing.getReadOnlyProperty();
    }

    /** Reads every provider's models again; an older reading still in flight is dropped. FX thread only. */
    public void refresh() {
        final long mine = ++epoch;
        final Map<String, String> names = new HashMap<>();
        viewModel.providers().forEach(row -> names.put(row.id(), viewModel.providerName(row.id())));
        log.debug("model roster reading #{} for {} provider(s)", mine, names.size());
        listing.set(true);
        background.execute(() -> {
            final List<ModelEntry> read = readOrNothing(mine, names);
            Platform.runLater(() -> publish(mine, read));
        });
    }

    // A reading that fails unexpectedly still ends: the list is empty and the tab stops saying it is reading.
    private List<ModelEntry> readOrNothing(final long reading, final Map<String, String> names) {
        try {
            return read(names);
        } catch (RuntimeException failure) {
            log.warn("model roster reading #{} failed", reading, failure);
            return List.of();
        }
    }

    private void publish(final long reading, final List<ModelEntry> read) {
        if (reading != epoch) {
            log.debug("model roster reading #{} dropped: the latest is #{}", reading, epoch);
            return;
        }
        entries.setAll(read);
        listing.set(false);
        log.debug("model roster reading #{} published {} model(s)", reading, read.size());
    }

    // Off the FX thread: the stores are read here and each provider is listed on the I/O executor.
    private List<ModelEntry> read(final Map<String, String> names) {
        final List<ProviderConfig> providers = ProviderRows.sorted(configs);
        final Map<String, List<String>> storedByProvider = storedModels();
        final List<Future<List<String>>> listed = providers.stream()
                .map(config -> io.submit(() -> listed(config.id())))
                .toList();
        final List<ModelEntry> result = new ArrayList<>();
        for (int i = 0; i < providers.size(); i++) {
            final ProviderConfig config = providers.get(i);
            final List<String> ids = ModelOrder.merge(
                    await(listed.get(i)), config.customModels(), storedByProvider.getOrDefault(config.id(), List.of()));
            ids.forEach(id -> result.add(
                    new ModelEntry(config.id(), names.getOrDefault(config.id(), config.id()), config.kind(), id)));
        }
        return result;
    }

    private Map<String, List<String>> storedModels() {
        final Result<List<ModelSettings>> all = modelSettings.all();
        final Map<String, List<String>> byProvider = new HashMap<>();
        if (all.isErr()) {
            log.warn(
                    "stored model settings unreadable for the model list: {}",
                    Objects.requireNonNull(all.error(), "error").code());
            return byProvider;
        }
        Objects.requireNonNull(all.data(), "data")
                .forEach(row -> byProvider
                        .computeIfAbsent(row.providerId(), key -> new ArrayList<>())
                        .add(row.modelId()));
        return byProvider;
    }

    private List<String> listed(final String providerId) {
        final Result<List<ModelInfo>> result = catalog.listModels(providerId);
        if (result.isErr()) {
            log.debug(
                    "models of '{}' not listed: {}",
                    providerId,
                    Objects.requireNonNull(result.error(), "error").code());
            return List.of();
        }
        return Objects.requireNonNull(result.data(), "data").stream()
                .map(ModelInfo::id)
                .toList();
    }

    private static List<String> await(final Future<List<String>> listed) {
        try {
            return listed.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException failed) {
            log.warn("a provider listing failed unexpectedly", failed);
            return List.of();
        }
    }
}
