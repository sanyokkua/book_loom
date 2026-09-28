package ua.bookloom.persistence.memory;

import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.persistence.contract.CheckpointContractTest;

/**
 * Proves the in-memory adapters against the shared repository contract (JUnit 5 creates a fresh instance per test
 * method, so each test gets an isolated {@link InMemoryStore}).
 */
class InMemoryRepositoryContractTest extends CheckpointContractTest {

    private final InMemoryStore store = new InMemoryStore();
    private final InMemoryProjectRepository projectRepository = new InMemoryProjectRepository(store);
    private final InMemorySegmentRepository segmentRepository = new InMemorySegmentRepository(store);
    private final InMemoryGlossaryRepository glossaryRepository = new InMemoryGlossaryRepository(store);
    private final InMemoryTmRepository tmRepository = new InMemoryTmRepository(store);
    private final InMemorySummaryRepository summaryRepository = new InMemorySummaryRepository(store);
    private final InMemoryDeferralRepository deferralRepository = new InMemoryDeferralRepository(store);
    private final InMemoryRunRepository runRepository = new InMemoryRunRepository(store);
    private final InMemoryCheckpoint checkpointPort = new InMemoryCheckpoint(store);

    @Override
    protected ProjectRepository projectRepository() {
        return projectRepository;
    }

    @Override
    protected SegmentRepository segmentRepository() {
        return segmentRepository;
    }

    @Override
    protected GlossaryRepository glossaryRepository() {
        return glossaryRepository;
    }

    @Override
    protected TmRepository tmRepository() {
        return tmRepository;
    }

    @Override
    protected SummaryRepository summaryRepository() {
        return summaryRepository;
    }

    @Override
    protected DeferralRepository deferralRepository() {
        return deferralRepository;
    }

    @Override
    protected RunRepository runRepository() {
        return runRepository;
    }

    @Override
    protected CheckpointPort checkpointPort() {
        return checkpointPort;
    }
}
