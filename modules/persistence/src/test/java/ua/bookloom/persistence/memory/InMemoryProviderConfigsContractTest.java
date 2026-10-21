package ua.bookloom.persistence.memory;

import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.persistence.contract.ProviderConfigsContractTest;

/** Proves the in-memory provider registry against the shared contract. */
class InMemoryProviderConfigsContractTest extends ProviderConfigsContractTest {

    private final ProviderConfigs configs = new InMemoryProviderConfigs();

    @Override
    protected ProviderConfigs configs() {
        return configs;
    }
}
