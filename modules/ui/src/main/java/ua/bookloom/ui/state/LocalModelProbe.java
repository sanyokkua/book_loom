package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.dialog.ModelPickPrompt;

/**
 * The look at the two local AI programs (ADR-0044): once on the very first launch, and again whenever the person
 * presses "Try again". It lists the models of exactly {@code http://127.0.0.1:11434} (Ollama) and
 * {@code http://127.0.0.1:1234/v1} (LM Studio), one request each, no book text, no inference.
 *
 * <p>A program that answers with at least one model is saved with the default timeouts (the look's short connect
 * timeout is for the look alone); one model is chosen at once, several are asked about. Nothing answering is not an
 * error. The look runs on the background executor; every property and the view model are touched on the FX thread.
 */
@Slf4j
@Singleton
public final class LocalModelProbe {

    private static final URI OLLAMA_ADDRESS = URI.create("http://127.0.0.1:11434");
    private static final URI LM_STUDIO_ADDRESS = URI.create("http://127.0.0.1:1234/v1");
    private static final String OLLAMA_ID = "ollama";
    private static final String LM_STUDIO_ID = "lmstudio";
    private static final Duration LOOK_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration LOOK_REQUEST_TIMEOUT = Duration.ofSeconds(2);

    /** One program that answered: how to save it and the models it listed. */
    private record Answered(ProviderConfig config, List<String> models) {}

    private final ModelCatalog catalog;
    private final ProviderConfigs configs;
    private final SettingsRepository settings;
    private final ExecutorService executor;
    private final SettingsViewModel viewModel;
    private final ModelPickPrompt pick;
    private final AtomicBoolean firstLaunchClaimed = new AtomicBoolean();
    private final ReadOnlyObjectWrapper<ProbeOutcome> outcome = new ReadOnlyObjectWrapper<>(new ProbeOutcome.Idle());

    /**
     * Receives the collaborators the injector owns.
     *
     * @param catalog the port the two addresses are listed through
     * @param configs the registry a program that answered is saved in
     * @param settings where the first-launch flag and the chosen model are kept
     * @param executor the daemon executor the look and the stores run on, never the FX thread
     * @param viewModel the Settings state that adopts the chosen model and re-reads the saved programs
     * @param pick the question asked when a program lists several models
     */
    @Inject
    public LocalModelProbe(
            final ModelCatalog catalog,
            final ProviderConfigs configs,
            final SettingsRepository settings,
            @BackgroundExecutor final ExecutorService executor,
            final SettingsViewModel viewModel,
            final ModelPickPrompt pick) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.pick = Objects.requireNonNull(pick, "pick");
    }

    /**
     * Where the look stands.
     *
     * @return a read-only property, changed on the FX thread only
     */
    public ReadOnlyObjectProperty<ProbeOutcome> outcome() {
        return outcome.getReadOnlyProperty();
    }

    /** Looks once, on the very first launch only: nothing happens when the flag says it has run. FX thread only. */
    public void runFirstLaunch() {
        if (!firstLaunchClaimed.compareAndSet(false, true)) {
            log.debug("first-launch look ignored: already requested in this session");
            return;
        }
        executor.execute(() -> {
            final Result<Optional<Boolean>> flag = settings.get(AppSetting.FIRST_LAUNCH_PROBED);
            if (flag.isErr()) {
                log.warn("the first-launch flag could not be read, no look is made");
                publish(new ProbeOutcome.Idle());
            } else if (Objects.requireNonNull(flag.data(), "data").orElse(false)) {
                log.debug("first-launch look skipped: it has run before");
                publish(new ProbeOutcome.Idle());
            } else {
                publish(new ProbeOutcome.Looking());
                look(true);
            }
        });
    }

    /** Looks again because the person asked, whatever the flag says. FX thread only. */
    public void tryAgain() {
        if (outcome.get() instanceof ProbeOutcome.Looking) {
            log.debug("try again ignored: a look is under way");
            return;
        }
        log.debug("try again requested");
        outcome.set(new ProbeOutcome.Looking());
        executor.execute(() -> look(false));
    }

    private void look(final boolean atFirstLaunch) {
        log.info("local AI program look starting, first launch {}", atFirstLaunch);
        try {
            final Optional<Answered> ollama =
                    ask(OLLAMA_ID, ProviderKind.OLLAMA, OLLAMA_ADDRESS).filter(this::isSameProgram);
            final Optional<Answered> lmStudio = ask(LM_STUDIO_ID, ProviderKind.OPENAI_COMPATIBLE, LM_STUDIO_ADDRESS)
                    .filter(this::isSameProgram);
            log.info("look finished: ollama={} lmstudio={}", count(ollama), count(lmStudio));
            ollama.ifPresent(this::save);
            lmStudio.ifPresent(this::save);
            final Optional<Answered> current = ollama.isPresent() ? ollama : lmStudio;
            conclude(current, atFirstLaunch);
        } catch (RuntimeException unexpected) {
            log.error("the look at the local AI programs failed unexpectedly", unexpected);
            markLooked();
            publish(new ProbeOutcome.NotFound(!atFirstLaunch));
        }
    }

    private static String count(final Optional<Answered> answered) {
        return answered.map(found -> Integer.toString(found.models().size())).orElse("none");
    }

    private Optional<Answered> ask(final String id, final ProviderKind kind, final URI address) {
        log.debug("asking {}", address);
        final ProviderConfig draft = new ProviderConfig(id, kind, address, LOOK_CONNECT_TIMEOUT, LOOK_REQUEST_TIMEOUT);
        final Result<List<ModelInfo>> listed = catalog.listModels(draft);
        if (listed.isErr()) {
            log.debug(
                    "{} did not answer: {}",
                    id,
                    Objects.requireNonNull(listed.error(), "error").code());
            return Optional.empty();
        }
        final List<String> models = Objects.requireNonNull(listed.data(), "data").stream()
                .map(ModelInfo::id)
                .toList();
        if (models.isEmpty()) {
            log.debug("{} answered with no models", id);
            return Optional.empty();
        }
        return Optional.of(new Answered(
                new ProviderConfig(
                        id,
                        kind,
                        address,
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT),
                models));
    }

    private boolean isSameProgram(final Answered answered) {
        final Optional<ProviderConfig> existing = configs.find(answered.config().id());
        final boolean same = existing.isEmpty()
                || sameAddress(existing.get().baseUrl(), answered.config().baseUrl());
        log.debug(
                "provider {} saved at another address, answer ignored: {}",
                answered.config().id(),
                !same);
        return same;
    }

    // "localhost" is the same program as 127.0.0.1: the registry's presets name it that way.
    private static boolean sameAddress(final URI saved, final URI looked) {
        return normalized(saved).equals(normalized(looked));
    }

    private static String normalized(final URI address) {
        return address.toString().replace("//localhost", "//127.0.0.1").replaceAll("/+$", "");
    }

    // A provider the person already set up under this id keeps its settings (name, timeouts, custom models); the look
    // never overwrites it, and a program at another address under the same id is not the one that answered.
    private void save(final Answered answered) {
        final Optional<ProviderConfig> existing = configs.find(answered.config().id());
        if (existing.isPresent()) {
            log.debug(
                    "provider {} is already saved, kept as it is",
                    answered.config().id());
            return;
        }
        final Result<ProviderConfig> saved = configs.save(answered.config());
        if (saved.isErr()) {
            log.warn(
                    "provider {} not saved: {}",
                    answered.config().id(),
                    Objects.requireNonNull(saved.error(), "error").code());
        }
    }

    private void conclude(final Optional<Answered> current, final boolean atFirstLaunch) {
        if (current.isEmpty()) {
            log.debug("nothing answered, after try again {}", !atFirstLaunch);
            markLooked();
            publish(new ProbeOutcome.NotFound(!atFirstLaunch));
        } else if (current.get().models().size() == 1) {
            final ModelSelection chosen = new ModelSelection(
                    current.get().config().id(), current.get().models().get(0));
            writeCurrent(chosen);
            markLooked();
            onFx(() -> viewModel.reloadProviders(() -> found(chosen, atFirstLaunch)));
        } else {
            markLooked();
            onFx(() -> viewModel.reloadProviders(() -> askWhich(current.get(), atFirstLaunch)));
        }
    }

    private void askWhich(final Answered answered, final boolean atFirstLaunch) {
        final String providerId = answered.config().id();
        outcome.set(new ProbeOutcome.Asking());
        pick.ask(
                providerId,
                answered.models(),
                modelId -> executor.execute(() -> pickedModel(new ModelSelection(providerId, modelId), atFirstLaunch)),
                () -> {
                    log.info("model pick declined: no current model is set");
                    outcome.set(new ProbeOutcome.Declined());
                });
    }

    private void pickedModel(final ModelSelection chosen, final boolean atFirstLaunch) {
        writeCurrent(chosen);
        onFx(() -> found(chosen, atFirstLaunch));
    }

    private void found(final ModelSelection chosen, final boolean atFirstLaunch) {
        viewModel.adopt(chosen);
        outcome.set(new ProbeOutcome.Found(chosen, atFirstLaunch));
    }

    private void writeCurrent(final ModelSelection chosen) {
        final Result<ModelSelection> written = settings.put(AppSetting.CURRENT_MODEL, chosen);
        if (written.isErr()) {
            log.warn(
                    "model.current not stored: {}",
                    Objects.requireNonNull(written.error(), "error").code());
        } else {
            log.info("model.current set to {}/{}", chosen.providerId(), chosen.modelId());
        }
    }

    private void markLooked() {
        final Result<Boolean> written = settings.put(AppSetting.FIRST_LAUNCH_PROBED, true);
        if (written.isErr()) {
            log.warn(
                    "the first-launch flag was not stored: {}",
                    Objects.requireNonNull(written.error(), "error").code());
        } else {
            log.info("first-launch look recorded");
        }
    }

    private void publish(final ProbeOutcome next) {
        onFx(() -> outcome.set(next));
    }

    private static void onFx(final Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
