package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.project.BookBrief;

/**
 * What {@link ProjectService#importBook} found and, when the file could be opened, made of it.
 *
 * @param projectId the new project's stable id, or null when the file was refused before a project was created
 * @param inspection what the pre-open inspection found
 * @param profile the opened book's profile, or null when the file was refused
 * @param brief the stored default brief with its preselected source language, present exactly when a project was
 *     created, so no screen re-derives the preselection
 */
public record ImportedBook(
        @Nullable String projectId,
        BookInspection inspection,
        @Nullable BookProfile profile,
        @Nullable BookBrief brief) {

    /** Rejects an imported book without its inspection result. */
    public ImportedBook {
        Objects.requireNonNull(inspection, "inspection");
    }
}
