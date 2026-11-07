package ua.bookloom.pipeline;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.proofread.ProofreadTools;

/**
 * The collaborators that decide and split a run's segments and proofread the decided text.
 *
 * @param qualityLoop the loop every segment is decided through
 * @param splitter the sentence splitter an oversized segment is drafted in pieces with
 * @param proofread the code sweep and deferral store the Proofread stage every run ends with works with
 * @param words the word check that tells a name from a common word
 */
public record RunChecks(
        QualityLoop qualityLoop, SentenceSplitter splitter, ProofreadTools proofread, WordValidator words) {

    /** Rejects a missing collaborator. */
    @Inject
    public RunChecks {
        Objects.requireNonNull(qualityLoop, "qualityLoop");
        Objects.requireNonNull(splitter, "splitter");
        Objects.requireNonNull(proofread, "proofread");
        Objects.requireNonNull(words, "words");
    }
}
