package ua.bookloom.pipeline;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.revision.ConsistencyPass;

/**
 * The collaborators that decide, split and revise a run's segments.
 *
 * @param qualityLoop the loop every segment is decided through
 * @param splitter the sentence splitter an oversized segment is drafted in pieces with
 * @param revision the backward-revision pass a Max run ends with
 * @param words the word check that tells a name from a common word
 */
public record RunChecks(
        QualityLoop qualityLoop, SentenceSplitter splitter, ConsistencyPass revision, WordValidator words) {

    /** Rejects a missing collaborator. */
    @Inject
    public RunChecks {
        Objects.requireNonNull(qualityLoop, "qualityLoop");
        Objects.requireNonNull(splitter, "splitter");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(words, "words");
    }
}
