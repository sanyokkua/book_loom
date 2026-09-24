package ua.bookloom.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.time.Clock;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.verify.ProviderVerifierImpl;

/**
 * Guice bindings owned by {@code :llm}, including the shared tolerant mapper for provider wire records.
 */
public final class LlmModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public LlmModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(ChatModelFactory.class).to(ChatModelFactoryImpl.class);
        bind(ProviderVerifier.class).to(ProviderVerifierImpl.class);
        bind(ProviderConfigs.class).to(InMemoryProviderConfigs.class).in(Singleton.class);
        bind(InferenceGate.class).in(Singleton.class);
        bind(ProviderClientFactory.class).in(Singleton.class);
    }

    /** Provides shared retry decisions with production clock, random jitter, and a blocking sleeper. */
    @Provides
    @Singleton
    public RetryPolicy retryPolicy() {
        return new RetryPolicy(Clock.systemUTC(), Math::random, delay -> Thread.sleep(delay.toMillis()));
    }

    /** Provides one shared HTTP client cache for both dialect clients. */
    @Provides
    @Singleton
    public HttpClients httpClients() {
        return new HttpClients();
    }

    /** Provides the shared exchange so every provider client uses the same timeout-keyed JDK clients. */
    @Provides
    @Singleton
    public HttpExchange httpExchange(HttpClients httpClients) {
        return new HttpExchange(httpClients);
    }

    /**
     * Provides the shared mapper used to read provider responses with fields this client does not consume.
     *
     * @return the singleton mapper that ignores unknown JSON properties
     */
    @Provides
    @Singleton
    public ObjectMapper objectMapper() {
        return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
