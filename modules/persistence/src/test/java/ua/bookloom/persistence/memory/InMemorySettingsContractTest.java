package ua.bookloom.persistence.memory;

import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.persistence.contract.SettingsContractTest;

/** Proves the in-memory settings adapter against the shared contract. */
class InMemorySettingsContractTest extends SettingsContractTest {

    private final InMemorySettingsRepository repository = new InMemorySettingsRepository();

    @Override
    protected SettingsRepository settings() {
        return repository;
    }
}
