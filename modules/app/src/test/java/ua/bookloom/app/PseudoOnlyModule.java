package ua.bookloom.app;

import com.google.inject.AbstractModule;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;

/** The workspace tests' one substitution: the provider registry lists only {@code pseudo}, so Settings selects it. */
final class PseudoOnlyModule extends AbstractModule {

    /** The pseudo provider; its address is never contacted, the factory answers {@code pseudo} itself. */
    static final ProviderConfig PSEUDO = new ProviderConfig(
            "pseudo",
            ProviderKind.OLLAMA,
            URI.create("http://localhost:1"),
            ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
            ProviderConfig.DEFAULT_REQUEST_TIMEOUT);

    @Override
    protected void configure() {
        bind(ProviderConfigs.class).toInstance(new PseudoOnlyProviderConfigs());
    }

    /** A registry of exactly one provider, {@link #PSEUDO}. */
    private static final class PseudoOnlyProviderConfigs implements ProviderConfigs {

        @Override
        public Result<ProviderConfig> save(final ProviderConfig config) {
            Objects.requireNonNull(config, "config");
            return Result.err(AppError.of(ErrorCode.validation, "Not supported", "This registry is fixed."));
        }

        @Override
        public Result<Boolean> delete(final String id) {
            Objects.requireNonNull(id, "id");
            return Result.err(AppError.of(ErrorCode.validation, "Not supported", "This registry is fixed."));
        }

        @Override
        public Optional<ProviderConfig> find(final String id) {
            Objects.requireNonNull(id, "id");
            return all().stream().filter(config -> config.id().equals(id)).findFirst();
        }

        @Override
        public List<ProviderConfig> all() {
            return List.of(PSEUDO);
        }
    }
}
