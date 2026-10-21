package ua.bookloom.app;

import com.google.inject.Injector;
import com.google.inject.Key;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.persistence.sqlite.SqliteDatabase;

/**
 * Two-phase startup, with the ordering enforced rather than described.
 *
 * <p>Phase 1 constructs services and performs no I/O. Phase 2 opens resources — the window's database, whose
 * schema is applied on open. Its <em>position</em> is the load-bearing part: the database must be open before any job
 * handle exists, and the first scene must be built after Phase 2 so a settings read has a database behind it.
 *
 * <p>A step that nothing checks gets quietly reordered or deleted, so the order is not merely recorded here, it is <em>enforced</em>: {@link #phaseTwo} refuses to run
 * before {@link #phaseOne}, and {@link #sceneBuilt} refuses to run before {@link #phaseTwo}. A future change that
 * moves resource-opening after the first scene fails immediately and loudly, rather than working until the first
 * settings read returns a default from a database that was not open yet.
 */
@Slf4j
public final class AppLifecycle {

    /** Services constructed; no I/O performed. */
    public static final String PHASE_ONE = "phase-1-construct";

    /** Resources opened: the database file, its schema applied. */
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
     * Phase 2: open resources — the database, when the graph holds one (the window's does, the command line's does
     * not).
     *
     * @param injector the built injector
     * @throws IllegalStateException if phase 1 has not completed
     * @throws com.google.inject.ProvisionException if the database cannot be opened, which is a startup error
     */
    public void phaseTwo(Injector injector) {
        Objects.requireNonNull(injector, "injector");
        require(PHASE_ONE, PHASE_TWO);
        if (injector.getExistingBinding(Key.get(SqliteDatabase.class)) != null) {
            injector.getInstance(SqliteDatabase.class);
            log.info("phase 2: database opened");
        } else {
            log.debug("phase 2: no database in this graph");
        }
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
     * Releases what phase 2 opened; called when the window stops.
     *
     * @param injector the injector of the run, or {@code null} when the application failed before building it
     */
    public void close(final @Nullable Injector injector) {
        if (injector != null && injector.getExistingBinding(Key.get(SqliteDatabase.class)) != null) {
            injector.getInstance(SqliteDatabase.class).close();
            log.info("database closed");
        }
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
