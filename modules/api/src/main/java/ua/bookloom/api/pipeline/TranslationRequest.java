package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The source, destination and language settings for one translation run.
 *
 * @param source the existing book to read
 * @param destination the same-format output path
 * @param targetLanguage the language to write to the output
 * @param sourceLanguage an explicitly selected source language, or null when the book declaration is used
 * @param overwrite whether an existing destination may be replaced after export validation
 */
public record TranslationRequest(
        Path source,
        Path destination,
        String targetLanguage,
        @Nullable String sourceLanguage,
        boolean overwrite) {

    /**
     * Rejects a request missing a path or target language; source-language absence is intentional.
     */
    public TranslationRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
    }
}
