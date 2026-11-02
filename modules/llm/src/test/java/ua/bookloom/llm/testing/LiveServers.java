package ua.bookloom.llm.testing;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.util.text.Slashes;

/** Builds the real {@code :llm} services for the env-gated live tests, which skip when their variable is unset. */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiveServers {

    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration REQUEST = Duration.ofMinutes(2);
    private static final int DRAFT_CAP = 256;

    /** The live address named by the variable, or a skipped test when it is unset. */
    public static String requireUrl(final String variable) {
        final String value = System.getenv(variable);
        Assumptions.assumeTrue(value != null && !value.isBlank(), variable + " is not set");
        return Slashes.trimTrailing(Objects.requireNonNull(value));
    }

    /** A provider with the given kind and address, the credential reference and no engine choice. */
    public static ProviderConfig provider(
            final String id, final ProviderKind kind, final String address, final @Nullable String credentialRef) {
        return new ProviderConfig(
                id,
                kind,
                URI.create(address),
                CONNECT,
                REQUEST,
                null,
                credentialRef == null ? ProviderAuth.NONE : ProviderAuth.BEARER,
                null,
                credentialRef,
                null,
                null,
                Map.of(),
                List.of(),
                kind == ProviderKind.OLLAMA ? ServerEngine.OLLAMA : ServerEngine.GENERIC,
                null,
                null,
                false);
    }

    /** One injector over the real services whose registry holds the given provider and reads keys from the map. */
    public static Injector injector(final ProviderConfig config, final Map<String, String> environment) {
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        if (!configs.save(config).isOk()) {
            throw new IllegalStateException("provider refused: " + config.id());
        }
        final Function<String, @Nullable String> env = environment::get;
        return Guice.createInjector(new LlmModule(env), new LlmTestStorageModule(configs));
    }

    /** The real verifier over a registry holding the provider. */
    public static ProviderVerifier verifier(final ProviderConfig config) {
        return injector(config, Map.of()).getInstance(ProviderVerifier.class);
    }

    /** The first model id the provider lists, for a live test whose model variable is unset. */
    public static String firstListedModel(final Injector injector, final String providerId) {
        final List<ModelInfo> models = Objects.requireNonNull(
                injector.getInstance(ModelCatalog.class).listModels(providerId).data(), "models");
        Assumptions.assumeFalse(models.isEmpty(), "the server lists no model");
        return models.get(0).id();
    }

    /** A chat model for the provider and model, built by the production factory. */
    public static ChatModel chatModel(final Injector injector, final String providerId, final String modelId) {
        return Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection(providerId, modelId))
                        .data(),
                "chat model");
    }

    /** A short draft call with the given reasoning level. */
    public static ChatRequest draft(final ReasoningLevel reasoning) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate to French: a red door.")),
                null,
                null,
                reasoning,
                null,
                null,
                DRAFT_CAP,
                null,
                null);
    }
}
