package ua.bookloom.pipeline.glossary;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * The services that read a book for its names: the scan that proposes them, the one that suggests their targets and
 * the one that reads the recurring terms.
 *
 * @param preScan proposes the names of a book
 * @param suggestTargets suggests the targets of names
 * @param lexiconReading reads the recurring terms
 */
public record NameServices(PreScan preScan, SuggestTargets suggestTargets, LexiconReading lexiconReading) {

    /** Rejects a missing service. */
    @Inject
    public NameServices {
        Objects.requireNonNull(preScan, "preScan");
        Objects.requireNonNull(suggestTargets, "suggestTargets");
        Objects.requireNonNull(lexiconReading, "lexiconReading");
    }
}
