package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * The model's proposal for the translated book's file name.
 *
 * @param name the file name without its extension, already cleaned of what a file name cannot hold; never blank
 * @param authorKeptInSourceScript whether the target language is written in another script than Latin but the name's
 *     author part is still in Latin letters after one correction was asked for, so the screen can say so
 */
public record FileNameSuggestion(String name, boolean authorKeptInSourceScript) {

    /** Rejects a missing name. */
    public FileNameSuggestion {
        Objects.requireNonNull(name, "name");
    }
}
