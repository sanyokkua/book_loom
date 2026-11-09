package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.pipeline.ExportService;

/**
 * What writes the book.
 *
 * @param exports builds and runs the export job
 */
public record ExportEngine(ExportService exports) {

    /** Rejects a missing part. */
    @Inject
    public ExportEngine {
        Objects.requireNonNull(exports, "exports");
    }
}
