package ua.bookloom.llm;

import com.google.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;

/** Process-local provider registry seeded with the two local server presets. */
@Slf4j
public final class InMemoryProviderConfigs implements ProviderConfigs {

    private final ConcurrentMap<String, ProviderConfig> configs = new ConcurrentHashMap<>();

    /** Seeds the default Ollama and LM Studio endpoints with the contract timeouts. */
    @Inject
    public InMemoryProviderConfigs() {
        configs.put("ollama", preset("ollama", ProviderKind.OLLAMA, "http://localhost:11434"));
        configs.put("lmstudio", preset("lmstudio", ProviderKind.OPENAI_COMPATIBLE, "http://localhost:1234/v1"));
        log.debug("Provider registry initialized presetIds=ollama,lmstudio");
    }

    @Override
    public Result<ProviderConfig> register(ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final String invalidDescription = validationFailure(config);
        if (invalidDescription != null) {
            log.warn("Provider registration refused id={} code={}", config.id(), ErrorCode.validation);
            return Result.err(AppError.of(ErrorCode.validation, "Invalid provider configuration", invalidDescription));
        }
        final ProviderConfig previous = configs.put(config.id(), config);
        log.debug(
                "Provider configuration registered id={} kind={} replaced={}",
                config.id(),
                config.kind(),
                previous != null);
        return Result.ok(config);
    }

    @Override
    public Optional<ProviderConfig> find(String id) {
        Objects.requireNonNull(id, "id");
        final ProviderConfig config = configs.get(id);
        log.debug("Provider configuration lookup id={} found={}", id, config != null);
        return Optional.ofNullable(config);
    }

    @Override
    public List<ProviderConfig> all() {
        final List<ProviderConfig> result = configs.values().stream()
                .sorted(Comparator.comparing(ProviderConfig::id))
                .toList();
        log.debug("Provider configuration list count={}", result.size());
        return result;
    }

    private static @Nullable String validationFailure(ProviderConfig config) {
        final URI baseUrl = config.baseUrl();
        final String scheme = baseUrl.getScheme();
        if (!baseUrl.isAbsolute()
                || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || baseUrl.getHost() == null) {
            return "The provider base URL must be an absolute HTTP or HTTPS URL.";
        }
        final Duration connectTimeout = config.connectTimeout();
        final Duration requestTimeout = config.requestTimeout();
        if (connectTimeout.isZero()
                || connectTimeout.isNegative()
                || requestTimeout.isZero()
                || requestTimeout.isNegative()) {
            return "Provider connect and request timeouts must both be positive.";
        }
        return null;
    }

    private static ProviderConfig preset(String id, ProviderKind kind, String url) {
        return new ProviderConfig(
                id,
                kind,
                URI.create(url),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }
}
