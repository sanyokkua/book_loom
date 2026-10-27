package ua.bookloom.api.pipeline;

import java.util.Objects;
import ua.bookloom.api.project.Project;

/**
 * What opening a stored project found: the book, or a book file that is gone or no longer the stored one. The last two
 * are data, not errors (ADR-0039), so a screen can offer to find the file.
 */
public sealed interface ProjectOpening {

    /**
     * The book is open with its stored brief and decided segments.
     *
     * @param book the opened book; its project id is never null
     * @param interrupted {@code true} when the stored run was RUNNING or PAUSED, which the open rewrote as cancelled
     */
    record Opened(ImportedBook book, boolean interrupted) implements ProjectOpening {

        /** Validates the book. */
        public Opened {
            Objects.requireNonNull(book, "book");
        }
    }

    /**
     * The file at the stored path is gone.
     *
     * @param project the stored project
     */
    record BookFileMissing(Project project) implements ProjectOpening {

        /** Validates the project. */
        public BookFileMissing {
            Objects.requireNonNull(project, "project");
        }
    }

    /**
     * The file at the stored path is another book than the one stored (its content hash differs) or cannot be read.
     *
     * @param project the stored project
     */
    record BookFileChanged(Project project) implements ProjectOpening {

        /** Validates the project. */
        public BookFileChanged {
            Objects.requireNonNull(project, "project");
        }
    }
}
