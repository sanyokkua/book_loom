package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.Project;

/**
 * What pointing a project at another file did: the book now lives there for every project of it, or the file is not
 * the stored book and nothing changed. A refusal is data, not an error (ADR-0039).
 */
public sealed interface BookRelocation {

    /**
     * The chosen file has the stored content hash and every project of the book now points at it.
     *
     * @param projects the book's projects as stored with the new path
     */
    record Relocated(List<Project> projects) implements BookRelocation {

        /** Copies the list. */
        public Relocated {
            projects = List.copyOf(Objects.requireNonNull(projects, "projects"));
        }
    }

    /**
     * The chosen file is unreadable or another book; nothing was stored.
     *
     * @param project the project that was asked to move
     */
    record DifferentBook(Project project) implements BookRelocation {

        /** Validates the project. */
        public DifferentBook {
            Objects.requireNonNull(project, "project");
        }
    }
}
