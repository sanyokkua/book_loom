package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the window still asks of a run itself — where the book is read from, where the translation is written and the
 * languages — until the window works on a stored project and keeps these on the project's brief and export request.
 *
 * @param source the book to read
 * @param destination the file the translation is written to after the run completes
 * @param targetLanguage the language to write
 * @param sourceLanguage an explicitly chosen source language, or null to keep the one the import preselected
 * @param overwrite whether an existing destination may be replaced
 */
public record InterimRunRequest(
        Path source,
        Path destination,
        String targetLanguage,
        @Nullable String sourceLanguage,
        boolean overwrite) {

    /** Rejects a request missing a path or the target language; the source language may be absent. */
    public InterimRunRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
    }
}
