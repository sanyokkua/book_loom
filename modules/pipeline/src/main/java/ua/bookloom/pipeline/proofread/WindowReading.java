package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.context.BookMap;

/**
 * The working state of the stage's window rounds: the records as committed so far, the opened book's story segments,
 * the verdict rules and the book map the windows are planned with. Used from the job thread only.
 *
 * @param state what the code part left
 * @param records the records by segment id, replaced as each window commits
 * @param segments the story segments by id, in reading order
 * @param verdicts the verdict rules of the stage
 * @param map the book map as the stage read it
 * @param locators the locator of every segment of the book, which the window's event names its segments by
 */
record WindowReading(
        ProofreadWindowPass.State state,
        Map<String, SegmentRecord> records,
        Map<String, Segment> segments,
        WindowVerdicts verdicts,
        BookMap map,
        Map<String, SegmentLocator> locators) {

    // A window names only readable story segments, so each one has a record and a segment.
    SegmentRecord record(final String id) {
        return Objects.requireNonNull(records.get(id), () -> "no record for " + id);
    }

    Segment segment(final String id) {
        return Objects.requireNonNull(segments.get(id), () -> "no segment for " + id);
    }

    /** {@return the window's sources and current texts, its neighbours' included, as committed now} */
    ProofreadCall.Texts texts(final ProofreadWindow window) {
        return texts(window, segments, records);
    }

    /**
     * A window's sources and current texts, its neighbours' included; the stored project's tally reads them the same.
     *
     * @param window the non-null window, which names only readable story segments
     * @param segments the non-null story segments by id
     * @param records the non-null stored records by id
     * @return never null
     */
    static ProofreadCall.Texts texts(
            final ProofreadWindow window,
            final Map<String, Segment> segments,
            final Map<String, SegmentRecord> records) {
        final Map<String, String> sources = new HashMap<>();
        final Map<String, String> currents = new HashMap<>();
        for (final String id : shownIds(window)) {
            sources.put(id, Objects.requireNonNull(segments.get(id), id).masked());
            currents.put(
                    id, Objects.requireNonNull(MarkList.maskedTarget(Objects.requireNonNull(records.get(id), id)), id));
        }
        return new ProofreadCall.Texts(sources, currents);
    }

    // The focus, then the neighbours a window has.
    private static List<String> shownIds(final ProofreadWindow window) {
        final List<String> ids = new ArrayList<>(window.focusIds());
        final @Nullable String before = window.beforeId();
        final @Nullable String after = window.afterId();
        if (before != null) {
            ids.add(before);
        }
        if (after != null) {
            ids.add(after);
        }
        return ids;
    }
}
