package ua.bookloom.llm.testing;

import com.google.inject.AbstractModule;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/** Binds the storage ports {@code :llm} needs by instance, in place of the {@code :persistence} adapters. */
public final class LlmTestStorageModule extends AbstractModule {

    private final ProviderConfigs configs;
    private final ModelSettingsRepository modelSettings;

    /** Binds fresh fakes. */
    public LlmTestStorageModule() {
        this(new FakeProviderConfigs());
    }

    /**
     * Binds the given registry.
     *
     * @param configs the registry the injector serves
     */
    public LlmTestStorageModule(final ProviderConfigs configs) {
        this.configs = configs;
        this.modelSettings = new FakeModelSettingsRepository();
    }

    @Override
    protected void configure() {
        bind(ProviderConfigs.class).toInstance(configs);
        bind(ModelSettingsRepository.class).toInstance(modelSettings);
    }
}
