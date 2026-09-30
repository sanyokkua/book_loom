package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where the completed run's book is written, and whether a file already there may be replaced, until the export
 * screen starts its own export.
 *
 * @param destination the file the translation is written to after the run completes
 * @param overwrite whether an existing destination may be replaced
 */
public record InterimRunRequest(Path destination, boolean overwrite) {

    /** Rejects a missing destination. */
    public InterimRunRequest {
        Objects.requireNonNull(destination, "destination");
    }
}
