package ua.bookloom.llm.engine;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;

/**
 * Detects a provider's engine and keeps it on the saved provider, so reading it later needs no network. Only a saved
 * provider with the same kind and address gets it stored: a description being edited is reported, not saved, and an
 * app that cannot translate a book is never stored as the engine. A detection also forgets what the session read
 * about the server's model, so the next call reads it afresh.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EngineRegistry {

    private final ProviderConfigs providerConfigs;
    private final EngineDetector detector;
    private final EngineFacts facts;

    /**
     * Detects as Save and Test do: the person's "Treat this server as..." choice stays and, when set, is the answer.
     *
     * @param config the provider description, saved or not
     * @return the engine found, or the failure that kept the server from being asked
     */
    public Result<EngineDetection> detect(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        log.debug("Engine detection for a test or save provider={}", config.id());
        final Result<EngineDetection> found = detector.detect(config);
        facts.forget(config.id());
        storeFound(config, found, false);
        return found;
    }

    /**
     * Detects as Detect again does: the person's choice is dropped so the probes decide, and a declined Ollama offer
     * stays declined.
     *
     * @param config the provider description, saved or not
     * @return the engine found, or the failure that kept the server from being asked
     */
    public Result<EngineDetection> detectAgain(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        log.debug("Engine detection asked again provider={} choiceDropped={}", config.id(), config.engineChoice());
        final ProviderConfig probed = config.withEngine(config.engine(), null, config.engineVersion());
        final Result<EngineDetection> found = detector.detect(probed);
        facts.forget(config.id());
        storeFound(config, found, true);
        return found;
    }

    private void storeFound(final ProviderConfig config, final Result<EngineDetection> found, final boolean again) {
        final EngineDetection detection = found.data();
        if (detection != null) {
            store(config, detection, again);
        }
    }

    private void store(final ProviderConfig config, final EngineDetection detection, final boolean again) {
        final Optional<ProviderConfig> saved = providerConfigs
                .find(config.id())
                .filter(stored ->
                        stored.kind() == config.kind() && stored.baseUrl().equals(config.baseUrl()));
        final boolean storable = detection.source() == Source.DETECTED
                && detection.suitability() == Suitability.SUITABLE
                && saved.isPresent();
        log.debug(
                "Engine store decision provider={} saved={} source={} suitability={} store={}",
                config.id(),
                saved.isPresent(),
                detection.source(),
                detection.suitability(),
                storable);
        if (!storable) {
            return;
        }
        final ProviderConfig stored = saved.orElseThrow();
        final ProviderConfig updated = again
                ? stored.detectedAgain(detection.engine(), detection.version())
                : stored.withEngine(detection.engine(), stored.engineChoice(), detection.version());
        if (!updated.equals(stored)) {
            save(updated);
        }
    }

    private void save(final ProviderConfig updated) {
        final Result<ProviderConfig> result = providerConfigs.save(updated);
        final AppError failure = result.error();
        if (failure != null) {
            log.warn("Engine could not be stored provider={} code={}", updated.id(), failure.code());
            return;
        }
        log.info(
                "Engine stored provider={} engine={} choice={}",
                updated.id(),
                updated.engine(),
                updated.engineChoice());
    }
}
