package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * Finds the earlier pairs a batch is shown beside its previous ones: the glossary names and recurring terms the batch's
 * sources name, then the accepted segments from earlier in the book that name the most of them. The run's switch turns
 * it off, in which case nothing is read.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class BatchRelevance {

    private final RunSettings settings;
    private final RelevantPairs pairs;

    /**
     * Creates the relevance of one run over the stored and the not yet stored decisions of its book.
     *
     * @param settings the non-null run settings
     * @param segments the non-null stored records
     * @param pending the non-null decisions of the run not yet stored
     * @param index the non-null index of the book the run started with
     * @param document the non-null book the index was built from
     * @return the relevance
     */
    static BatchRelevance of(
            final RunSettings settings,
            final SegmentRepository segments,
            final PendingCommit pending,
            final BookIndex index,
            final Document document) {
        return new BatchRelevance(
                settings, new RelevantPairs(segments, settings.projectId(), pending, index, document));
    }

    /**
     * Creates the relevance of one run.
     *
     * @param settings the non-null run settings, whose switch decides whether any pair is looked for
     * @param pairs the non-null selection of the accepted earlier pairs
     */
    BatchRelevance(final RunSettings settings, final RelevantPairs pairs) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.pairs = Objects.requireNonNull(pairs, "pairs");
    }

    /**
     * The relevant pairs of a batch.
     *
     * @param current the non-null chunk being drafted, whose glossary and lexicon name the terms
     * @param batch the non-null segments of the batch, in document order
     * @param preceding the non-null batch's preceding pairs, which are not repeated
     * @return up to {@link RelevantPairs#LIMIT} pairs, best first; empty when the switch is off or no accepted segment
     *     shares a term; or the repository's error
     */
    Result<List<EarlierPair>> choose(
            final Current current, final List<Segment> batch, final List<EarlierPair> preceding) {
        if (!settings.relevantPairs()) {
            log.debug("Relevant pairs are off");
            return Result.ok(List.of());
        }
        final List<String> terms = current.context().sharedTermsIn(batch);
        log.debug(
                "Looking for relevant pairs segmentId={} terms={}",
                batch.getFirst().id(),
                terms.size());
        log.trace("Relevant pairs terms={}", terms);
        return pairs.select(terms, batch.getFirst(), preceding);
    }
}
