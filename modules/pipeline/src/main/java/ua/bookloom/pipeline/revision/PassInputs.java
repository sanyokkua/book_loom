package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * What one pass reads once before it changes anything: the stored brief's call frame, the opened book's segments and
 * locators, the document gate every new target is restored through, and the glossary as the pass starts.
 *
 * @param projectId the project the pass revises
 * @param frame the languages, style sheet and foreign-passage policy built from the stored brief
 * @param namePolicy the brief's name policy, read by the checks a re-render passes
 * @param gate the document gate over the opened book's format
 * @param sources each segment of the opened book by id
 * @param locators each segment's locator by id, which the report's notes name
 * @param glossary every glossary entry as the pass starts
 * @param order the opened book's segment ids in reading order, which the neighbours of a paragraph are read from
 * @param book the brief, the opened book and the glossary as the audit reads them, so a new text is never one the
 *     audit doubts more than the old
 * @param context the recurring-term lexicon and the rolling summary the check against the neighbours shows
 */
record PassInputs(
        String projectId,
        CallFrame frame,
        NamePolicy namePolicy,
        GateFunction gate,
        Map<String, Segment> sources,
        Map<String, SegmentLocator> locators,
        List<GlossaryEntry> glossary,
        List<String> order,
        FinalAudit.Book book,
        BookContext context) {

    /** Rejects a missing component and copies the collections. */
    PassInputs {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(gate, "gate");
        sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
        locators = Map.copyOf(Objects.requireNonNull(locators, "locators"));
        glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
        order = List.copyOf(Objects.requireNonNull(order, "order"));
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(context, "context");
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

    /**
     * How the report names a segment.
     *
     * @param segmentId the segment id
     * @return its locator text, or the id itself for a segment the opened book does not locate
     */
    String locator(final String segmentId) {
        final SegmentLocator locator = locators.get(segmentId);
        return locator == null ? segmentId : locator.text();
    }
}
