package ua.bookloom.persistence.memory;

import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.persistence.contract.ModelSettingsContractTest;

/** Proves the in-memory per-model settings adapter against the shared contract. */
class InMemoryModelSettingsContractTest extends ModelSettingsContractTest {

    private final InMemoryModelSettingsRepository repository = new InMemoryModelSettingsRepository();

    @Override
    protected ModelSettingsRepository repository() {
        return repository;
    }
}
