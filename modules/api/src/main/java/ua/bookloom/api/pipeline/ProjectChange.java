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
     */
    record Saved(Project project) implements ProjectChange {

        /** Validates the project. */
        public Saved {
            Objects.requireNonNull(project, "project");
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
