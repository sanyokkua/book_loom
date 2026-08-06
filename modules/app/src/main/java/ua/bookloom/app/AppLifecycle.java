package ua.bookloom.app;

import com.google.inject.Injector;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Two-phase startup, with the ordering enforced rather than described.
 *
 * <p>Phase 1 constructs services and performs no I/O. Phase 2 opens resources — it is where database migrations
 * will run and repositories will be backfilled. <strong>Phase 2 is deliberately empty today</strong>, because this
 * change ships no persistence at all, and it exists anyway because its <em>position</em> is the load-bearing part:
 * migrations must complete before any job handle exists, and the first scene must be built after Phase 2 so a
 * settings read has a database behind it.
 *
 * <p>An empty step that nothing checks is an empty step that gets quietly reordered or deleted by the change that
 * fills it — which is the exact accident reserving it was meant to prevent, arriving by the exact route it was meant
 * to block. So the order is not merely recorded here, it is <em>enforced</em>: {@link #phaseTwo} refuses to run
 * before {@link #phaseOne}, and {@link #sceneBuilt} refuses to run before {@link #phaseTwo}. A future change that
 * moves resource-opening after the first scene fails immediately and loudly, rather than working until the first
 * settings read returns a default from a database that was not open yet.
 */
public final class AppLifecycle {

    /** Services constructed; no I/O performed. */
    public static final String PHASE_ONE = "phase-1-construct";

    /** Resources opened: migrations, repositories. Empty until the local-storage change fills it. */
    public static final String PHASE_TWO = "phase-2-open-resources";

    /** The first scene is built and shown. */
    public static final String SCENE = "scene-built";

    private final List<String> completed = new CopyOnWriteArrayList<>();

    /**
     * Phase 1: construct services from the assembled graph, touching no I/O.
     *
     * @param injector the built injector
     */
    public void phaseOne(Injector injector) {
        Objects.requireNonNull(injector, "injector");
        // Nothing to construct eagerly yet. The injector having assembled at all is this change's real signal:
        // a graph spanning eight JPMS modules either resolves or it does not.
        completed.add(PHASE_ONE);
    }

    /**
     * Phase 2: open resources. Empty by design — see the class comment.
     *
     * @param injector the built injector
     * @throws IllegalStateException if phase 1 has not completed
     */
    public void phaseTwo(Injector injector) {
        Objects.requireNonNull(injector, "injector");
        require(PHASE_ONE, PHASE_TWO);
        // The local-storage change fills this: open SQLite in WAL mode, run Flyway, backfill repositories. It must
        // fill the BODY and leave the position alone.
        completed.add(PHASE_TWO);
    }

    /**
     * Records that the first scene has been built.
     *
     * @throws IllegalStateException if phase 2 has not completed
     */
    public void sceneBuilt() {
        require(PHASE_TWO, SCENE);
        completed.add(SCENE);
    }

    /**
     * The steps that have completed, in the order they ran.
     *
     * @return an immutable snapshot
     */
    public List<String> completedSteps() {
        return List.copyOf(completed);
    }

    private void require(String prerequisite, String step) {
        if (!completed.contains(prerequisite)) {
            throw new IllegalStateException(step + " ran before " + prerequisite
                    + "; the startup order is a contract, not a convention (10_DI_AND_LIFECYCLE.md#two-phase-init)");
        }
    }
}
