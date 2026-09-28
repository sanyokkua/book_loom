package ua.bookloom.persistence;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.persistence.memory.InMemoryCheckpoint;
import ua.bookloom.persistence.memory.InMemoryDeferralRepository;
import ua.bookloom.persistence.memory.InMemoryGlossaryRepository;
import ua.bookloom.persistence.memory.InMemoryProjectRepository;
import ua.bookloom.persistence.memory.InMemoryRunRepository;
import ua.bookloom.persistence.memory.InMemorySegmentRepository;
import ua.bookloom.persistence.memory.InMemorySummaryRepository;
import ua.bookloom.persistence.memory.InMemoryTmRepository;

/**
 * Guice bindings owned by {@code :persistence} — every repository port bound to its in-memory adapter, all sharing
 * one shared in-memory store singleton per injector ({@code ua.bookloom.persistence.memory.InMemoryStore}, scoped by
 * its own class-level {@code @Singleton} rather than bound here, since it is package-private to {@code .memory}
 * (ADR-0034). A later change rebinds these ports to SQLite-backed adapters without touching a caller.
 */
public final class PersistenceModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public PersistenceModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(ProjectRepository.class).to(InMemoryProjectRepository.class).in(Singleton.class);
        bind(SegmentRepository.class).to(InMemorySegmentRepository.class).in(Singleton.class);
        bind(GlossaryRepository.class).to(InMemoryGlossaryRepository.class).in(Singleton.class);
        bind(TmRepository.class).to(InMemoryTmRepository.class).in(Singleton.class);
        bind(SummaryRepository.class).to(InMemorySummaryRepository.class).in(Singleton.class);
        bind(DeferralRepository.class).to(InMemoryDeferralRepository.class).in(Singleton.class);
        bind(RunRepository.class).to(InMemoryRunRepository.class).in(Singleton.class);
        bind(CheckpointPort.class).to(InMemoryCheckpoint.class).in(Singleton.class);
    }
}
