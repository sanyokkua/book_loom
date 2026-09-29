package ua.bookloom.pipeline.export;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What {@link BookExporter} needs to write one book.
 *
 * @param source the source file, opened afresh so a changed source is noticed
 * @param destination the file to publish
 * @param targetLanguage the language tag the written book declares
 * @param overwrite whether an existing destination may be replaced
 */
public record ExportPlan(Path source, Path destination, String targetLanguage, boolean overwrite) {

    /** Rejects a plan without its paths or language. */
    public ExportPlan {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
    }
}
