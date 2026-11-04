package ua.bookloom.api.pipeline;

import java.util.Objects;
import ua.bookloom.api.project.Project;

/**
 * What saving a project's brief or name did: stored it, or found a clash inside the book and stored nothing. A clash
 * is data, not an error (ADR-0039).
 */
public sealed interface ProjectChange {

    /**
     * The change was stored.
     *
     * @param project the project as stored
     * @param rechecked how many decided segments a changed Brief key left with a new finding to look at; 0 for a
     *     change that re-checks nothing
     */
    record Saved(Project project, int rechecked) implements ProjectChange {

        /** Validates the project and the count. */
        public Saved {
            Objects.requireNonNull(project, "project");
            if (rechecked < 0) {
                throw new IllegalArgumentException("rechecked must not be negative: " + rechecked);
            }
        }
    }

    /**
     * Another project of the book already translates this language pair and the change needed an unnamed one.
     *
     * @param existing the project that holds the pair
     */
    record PairInUse(Project existing) implements ProjectChange {

        /** Validates the project. */
        public PairInUse {
            Objects.requireNonNull(existing, "existing");
        }
    }

    /**
     * Another project of the same book already has the name, ignoring letter case.
     *
     * @param holder the project that has the name
     */
    record NameInUse(Project holder) implements ProjectChange {

        /** Validates the project. */
        public NameInUse {
            Objects.requireNonNull(holder, "holder");
        }
    }
}
