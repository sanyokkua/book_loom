package ua.bookloom.pipeline.proofread;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.heal.GateFunction;

/**
 * What the term sweep reads of the run: the book's segments to restore a swept target against, the document gate every
 * swept target is restored through, and the language whose forms a pinned target declines in.
 *
 * @param projectId the project the sweep runs on
 * @param targetLanguage the BCP 47 tag of the target language
 * @param gate the document gate over the opened book's format, around the run's typography
 * @param sources each segment of the opened book by id
 */
public record SweepInputs(String projectId, String targetLanguage, GateFunction gate, Map<String, Segment> sources) {

    /** Rejects a missing component and copies the map. */
    public SweepInputs {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(gate, "gate");
        sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    /**
     * The opened book's segment.
     *
     * @param segmentId the segment id
     * @return the segment if the opened book holds it, or empty if it does not
     */
    Optional<Segment> source(final String segmentId) {
        return Optional.ofNullable(sources.get(segmentId));
    }
}
